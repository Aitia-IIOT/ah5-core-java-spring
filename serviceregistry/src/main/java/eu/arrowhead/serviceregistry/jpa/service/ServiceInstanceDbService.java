package eu.arrowhead.serviceregistry.jpa.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.type.TypeReference;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.InternalServerError;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.common.service.validation.MetadataRequirementsMatcher;
import eu.arrowhead.dto.MetadataRequirementDTO;
import eu.arrowhead.dto.ServiceInstanceInterfaceRequestDTO;
import eu.arrowhead.dto.ServiceInstanceRequestDTO;
import eu.arrowhead.dto.enums.ServiceInterfacePolicy;
import eu.arrowhead.serviceregistry.ServiceRegistrySystemInfo;
import eu.arrowhead.serviceregistry.jpa.entity.ServiceDefinition;
import eu.arrowhead.serviceregistry.jpa.entity.ServiceInstance;
import eu.arrowhead.serviceregistry.jpa.entity.ServiceInstanceInterface;
import eu.arrowhead.serviceregistry.jpa.entity.ServiceInterfaceTemplate;
import eu.arrowhead.serviceregistry.jpa.entity.ServiceInterfaceTemplateProperty;
import eu.arrowhead.serviceregistry.jpa.entity.System;
import eu.arrowhead.serviceregistry.jpa.repository.ServiceDefinitionRepository;
import eu.arrowhead.serviceregistry.jpa.repository.ServiceInstanceInterfaceRepository;
import eu.arrowhead.serviceregistry.jpa.repository.ServiceInstanceRepository;
import eu.arrowhead.serviceregistry.jpa.repository.ServiceInterfaceTemplatePropertyRepository;
import eu.arrowhead.serviceregistry.jpa.repository.ServiceInterfaceTemplateRepository;
import eu.arrowhead.serviceregistry.jpa.repository.SystemRepository;
import eu.arrowhead.serviceregistry.service.ServiceDiscoveryInterfacePolicy;
import eu.arrowhead.serviceregistry.service.model.ServiceLookupFilterModel;
import eu.arrowhead.serviceregistry.service.utils.ServiceInstanceIdUtils;

@Service
public class ServiceInstanceDbService {

	//=================================================================================================
	// members

	@Autowired
	private ServiceRegistrySystemInfo sysInfo;

	@Autowired
	private ServiceInstanceRepository serviceInstanceRepo;

	@Autowired
	private ServiceInstanceInterfaceRepository serviceInstanceInterfaceRepo;

	@Autowired
	private SystemRepository systemRepo;

	@Autowired
	private ServiceDefinitionRepository serviceDefinitionRepo;

	@Autowired
	private ServiceInterfaceTemplateRepository serviceInterfaceTemplateRepo;

	@Autowired
	private ServiceInterfaceTemplatePropertyRepository serviceInterfaceTemplatePropsRepo;

	private static final Object LOCK = new Object();

	private final Logger logger = LogManager.getLogger(this.getClass());

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@Transactional(rollbackFor = ArrowheadException.class)
	public List<Entry<ServiceInstance, List<ServiceInstanceInterface>>> createBulk(final List<ServiceInstanceRequestDTO> candidates) {
		logger.debug("createBulk started");
		Assert.isTrue(!Utilities.isEmpty(candidates), "service instance candidate list is empty");

		final Map<String, System> systemCache = new HashMap<>();
		final Map<String, ServiceDefinition> definitionCache = new HashMap<>();
		final Map<String, ServiceInterfaceTemplate> interfaceTemplateCache = new HashMap<>();

		try {
			List<ServiceInstance> instanceEntities = new ArrayList<>(candidates.size());
			List<ServiceInstanceInterface> instanceInterfaceEntities = new ArrayList<>();

			synchronized (LOCK) {
				// Caching necessary entities
				final Set<String> systemNames = new HashSet<>();
				final Set<String> serviceDefinitionNames = new HashSet<>();
				final Set<String> serviceInterfaceTemlateNames = new HashSet<>();

				for (final ServiceInstanceRequestDTO candidate : candidates) {
					systemNames.add(candidate.systemName());
					serviceDefinitionNames.add(candidate.serviceDefinitionName());
					serviceInterfaceTemlateNames.addAll(candidate.interfaces().stream().map(template -> template.templateName()).collect(Collectors.toSet()));
				}

				systemRepo.findAllByNameIn(systemNames).forEach(system -> systemCache.put(system.getName(), system));
				serviceDefinitionRepo.findAllByNameIn(serviceDefinitionNames).forEach(definition -> definitionCache.put(definition.getName(), definition));
				serviceInterfaceTemplateRepo.findAllByNameIn(serviceInterfaceTemlateNames).forEach(template -> interfaceTemplateCache.put(template.getName(), template));

				// Validating interface template names in case of restricted interface policy
				if (sysInfo.getServiceDiscoveryInterfacePolicy() == ServiceDiscoveryInterfacePolicy.RESTRICTED) {
					serviceInterfaceTemlateNames.forEach(templateName -> {
						if (!interfaceTemplateCache.containsKey(templateName)) {
							throw new InvalidParameterException("Interface template not exists: " + templateName);
						}
					});
				}

				// Create instance records
				instanceEntities = createInstances(candidates, systemCache, definitionCache);

				// Create instance interface records
				instanceInterfaceEntities = createInstanceInterfaces(candidates, instanceEntities, interfaceTemplateCache);
			}

			// Prepare result structure
			final List<Entry<ServiceInstance, List<ServiceInstanceInterface>>> results = new ArrayList<>(candidates.size());
			for (final ServiceInstance instance : instanceEntities) {
				results.add(Map.entry(
						instance,
						instanceInterfaceEntities.stream().filter(interf -> interf.getServiceInstance().getServiceInstanceId().equals(instance.getServiceInstanceId())).toList()));
			}

			return results;

		} catch (final InvalidParameterException ex) {
			throw ex;

		} catch (final Exception ex) {
			logger.error(ex.getMessage());
			logger.debug(ex);
			throw new InternalServerError("Database operation error");
		}
	}

	//-------------------------------------------------------------------------------------------------
	public Optional<ServiceInstance> getByInstanceId(final String serviceInstanceId) {
		logger.debug("getByInstanceId started");
		Assert.isTrue(!Utilities.isEmpty(serviceInstanceId), "serviceInstanceId is empty");

		try {
			return serviceInstanceRepo.findByServiceInstanceId(serviceInstanceId);

		} catch (final Exception ex) {
			logger.error(ex.getMessage());
			logger.debug(ex);
			throw new InternalServerError("Database operation error");
		}
	}

	//-------------------------------------------------------------------------------------------------
	public Page<Entry<ServiceInstance, List<ServiceInstanceInterface>>> getPageByFilters(final PageRequest pagination, final ServiceLookupFilterModel filters) {
		logger.debug("getPageByFilters started");
		Assert.notNull(pagination, "page is null");
		try {
			if (!filters.hasFilters()) {
				return findAll(pagination);
			}
			return findAllByFilters(pagination, filters);

		} catch (final Exception ex) {
			logger.error(ex.getMessage());
			logger.debug(ex);
			throw new InternalServerError("Database operation error");
		}
	}

	//-------------------------------------------------------------------------------------------------
	@Transactional(rollbackFor = ArrowheadException.class)
	public boolean deleteByInstanceId(final String serviceInstanceId) {
		logger.debug("deleteByInstanceId started");
		Assert.isTrue(!Utilities.isEmpty(serviceInstanceId), "serviceInstanceId is empty");

		try {
			final Optional<ServiceInstance> optional = serviceInstanceRepo.findByServiceInstanceId(serviceInstanceId);
			if (optional.isPresent()) {
				serviceInstanceRepo.delete(optional.get());
				serviceInstanceRepo.flush();
				return true;
			}

			return false;

		} catch (final Exception ex) {
			logger.error(ex.getMessage());
			logger.debug(ex);
			throw new InternalServerError("Database operation error");
		}
	}

	//=================================================================================================
	// assistant methods

	//-------------------------------------------------------------------------------------------------
	private List<ServiceInstance> createInstances(final List<ServiceInstanceRequestDTO> candidates, final Map<String, System> systemCache, final Map<String, ServiceDefinition> definitionCache) {
		final List<ServiceInstance> instanceEntities = new ArrayList<>(candidates.size());

		for (final ServiceInstanceRequestDTO candidate : candidates) {
			if (!systemCache.containsKey(candidate.systemName())) {
				throw new InvalidParameterException("System not exists: " + candidate.systemName());
			}
			if (!definitionCache.containsKey(candidate.serviceDefinitionName())) {
				definitionCache.put(
						candidate.serviceDefinitionName(),
						serviceDefinitionRepo.saveAndFlush(new ServiceDefinition(candidate.serviceDefinitionName())));
			}
			final String instanceId = ServiceInstanceIdUtils.calculateInstanceId(candidate.systemName(), candidate.serviceDefinitionName(), candidate.version());

			instanceEntities.add(new ServiceInstance(
					instanceId,
					systemCache.get(candidate.systemName()),
					definitionCache.get(candidate.serviceDefinitionName()),
					candidate.version(),
					Utilities.parseUTCStringToZonedDateTime(candidate.expiresAt()),
					Utilities.toJson(candidate.metadata())));
		}

		return serviceInstanceRepo.saveAllAndFlush(instanceEntities);
	}

	//-------------------------------------------------------------------------------------------------
	private List<ServiceInstanceInterface> createInstanceInterfaces(final List<ServiceInstanceRequestDTO> candidates, final List<ServiceInstance> instanceEntities, final Map<String, ServiceInterfaceTemplate> interfaceTemplateCache) {
		final List<ServiceInstanceInterface> instanceInterfaceEntities = new ArrayList<>();
		final Set<String> templatesCreated = new HashSet<>();

		for (final ServiceInstanceRequestDTO candidate : candidates) {
			final ServiceInstance serviceInstance = instanceEntities
					.stream()
					.filter(i -> i.getServiceInstanceId().equals(ServiceInstanceIdUtils.calculateInstanceId(candidate.systemName(), candidate.serviceDefinitionName(), candidate.version())))
					.findFirst()
					.get();

			for (final ServiceInstanceInterfaceRequestDTO interfaceCandidate : candidate.interfaces()) {
				Assert.isTrue(Utilities.isEnumValue(interfaceCandidate.policy(), ServiceInterfacePolicy.class), "Invalid service interface policy");

				if (!interfaceTemplateCache.containsKey(interfaceCandidate.templateName())) {
					if (Utilities.isEmpty(interfaceCandidate.protocol())) {
						throw new InvalidParameterException("No protocol has been defined");
					}

					interfaceTemplateCache.put(
							interfaceCandidate.templateName(),
							serviceInterfaceTemplateRepo.saveAndFlush(new ServiceInterfaceTemplate(interfaceCandidate.templateName(), interfaceCandidate.protocol())));
					templatesCreated.add(interfaceCandidate.templateName());

					if (sysInfo.getServiceDiscoveryInterfacePolicy() == ServiceDiscoveryInterfacePolicy.EXTENDABLE) {
						final List<ServiceInterfaceTemplateProperty> templateProps = new ArrayList<>();
						interfaceCandidate.properties().keySet().forEach(propName -> {
							templateProps.add(new ServiceInterfaceTemplateProperty(
									interfaceTemplateCache.get(interfaceCandidate.templateName()),
									propName,
									true,
									null));
						});
						serviceInterfaceTemplatePropsRepo.saveAllAndFlush(templateProps);
					}
				}

				// Need to validate the properties if the template was created in this bulk operation (only in case of EXTENDABLE policy)
				if (templatesCreated.contains(interfaceCandidate.templateName()) && sysInfo.getServiceDiscoveryInterfacePolicy() == ServiceDiscoveryInterfacePolicy.EXTENDABLE) {
					serviceInterfaceTemplatePropsRepo.findByServiceInterfaceTemplate(interfaceTemplateCache.get(interfaceCandidate.templateName())).forEach(templateProp -> {
						if (templateProp.isMandatory() && !interfaceCandidate.properties().containsKey(templateProp.getPropertyName())) {
							throw new InvalidParameterException("Mandatory interface property is missing: " + templateProp.getPropertyName());
						}
					});
				}

				if (!Utilities.isEmpty(interfaceCandidate.protocol()) && !interfaceTemplateCache.get(interfaceCandidate.templateName()).getProtocol().equalsIgnoreCase(interfaceCandidate.protocol())) {
					throw new InvalidParameterException("Interface has different protocol than " + interfaceCandidate.templateName() + " template");
				}

				instanceInterfaceEntities.add(new ServiceInstanceInterface(
						serviceInstance,
						interfaceTemplateCache.get(interfaceCandidate.templateName()),
						Utilities.toJson(interfaceCandidate.properties()),
						ServiceInterfacePolicy.valueOf(interfaceCandidate.policy())));
			}
		}

		return serviceInstanceInterfaceRepo.saveAllAndFlush(instanceInterfaceEntities);
	}

	//-------------------------------------------------------------------------------------------------
	private Page<Entry<ServiceInstance, List<ServiceInstanceInterface>>> findAll(final PageRequest pagination) {
		final Map<ServiceInstance, List<ServiceInstanceInterface>> serviceInterfaceMap = new HashMap<>();

		final Page<ServiceInstance> page = serviceInstanceRepo.findAll(pagination);
		for (final ServiceInstance serviceInstance : page) {
			serviceInterfaceMap.putIfAbsent(serviceInstance, new ArrayList<>());
			serviceInterfaceMap.get(serviceInstance).addAll(serviceInstanceInterfaceRepo.findAllByServiceInstance(serviceInstance));
		}

		return new PageImpl<>(new ArrayList<>(serviceInterfaceMap.entrySet()), pagination, page.getTotalElements());
	}

	//-------------------------------------------------------------------------------------------------
	private Page<Entry<ServiceInstance, List<ServiceInstanceInterface>>> findAllByFilters(final PageRequest pagination, final ServiceLookupFilterModel filters) {
		final Set<Long> matchingIds = new HashSet<>();
		final Map<Long, List<ServiceInstanceInterface>> serviceInterfaceMap = new HashMap<>();

		BaseFilter baseFilter = BaseFilter.NONE;
		synchronized (LOCK) {
			List<ServiceInstance> toFilter = new ArrayList<>();
			if (!Utilities.isEmpty(filters.getInstanceIds())) {
				toFilter = serviceInstanceRepo.findAllByServiceInstanceIdIn(filters.getInstanceIds());
				baseFilter = BaseFilter.INSTANCE_ID;
			} else if (!Utilities.isEmpty(filters.getProviderNames())) {
				toFilter = serviceInstanceRepo.findAllBySystem_NameIn(filters.getProviderNames());
				baseFilter = BaseFilter.SERVICE_NAME;
			} else if (!Utilities.isEmpty(filters.getServiceDefinitionNames())) {
				toFilter = serviceInstanceRepo.findAllByServiceDefinition_NameIn(filters.getServiceDefinitionNames());
				baseFilter = BaseFilter.SERVICE_NAME;
			} else {
				toFilter = serviceInstanceRepo.findAll();
			}

			final ZonedDateTime now = Utilities.utcNow();
			final Set<Long> deleteIds = new HashSet<>();
			for (final ServiceInstance serviceCandidate : toFilter) {

				// Check expiration date due to delete on demand
				if (serviceCandidate.getExpiresAt() != null && now.isAfter(serviceCandidate.getExpiresAt())) {
					deleteIds.add(serviceCandidate.getId());
					continue;
				}

				boolean matching = true;

				// Match against to service instance id requirements
				if (baseFilter != BaseFilter.INSTANCE_ID && !Utilities.isEmpty(filters.getInstanceIds()) && !filters.getInstanceIds().contains(serviceCandidate.getServiceInstanceId())) {
					matching = false;
				}

				// Match against to provider name requirements
				if (matching && baseFilter != BaseFilter.SYSTEM_NAME && !Utilities.isEmpty(filters.getProviderNames()) && !filters.getProviderNames().contains(serviceCandidate.getSystem().getName())) {
					matching = false;
				}

				// Match against to service definition requirements
				if (matching && baseFilter != BaseFilter.SERVICE_NAME && !Utilities.isEmpty(filters.getServiceDefinitionNames())
						&& !filters.getServiceDefinitionNames().contains(serviceCandidate.getServiceDefinition().getName())) {
					matching = false;
				}

				// Match against to version requirements
				if (matching && !Utilities.isEmpty(filters.getVersions()) && !filters.getVersions().contains(serviceCandidate.getVersion())) {
					matching = false;
				}

				// Match against to alive requirements
				if (matching && filters.getAlivesAt() != null && serviceCandidate.getExpiresAt() != null && filters.getAlivesAt().isBefore(serviceCandidate.getExpiresAt())) {
					matching = false;
				}

				// Match against to metadata requirements
				if (matching && !Utilities.isEmpty(filters.getMetadataRequirementsList())) {
					boolean metadataMatch = false;
					final Map<String, Object> instanceMetadata = Utilities.fromJson(serviceCandidate.getMetadata(), new TypeReference<Map<String, Object>>() { });
					for (final MetadataRequirementDTO requirement : filters.getMetadataRequirementsList()) {
						if (MetadataRequirementsMatcher.isMetadataMatch(instanceMetadata, requirement)) {
							metadataMatch = true;
							break;
						}
					}
					if (!metadataMatch) {
						matching = false;
					}
				}

				// Match against to interface related requirements
				if (matching) {
					final List<ServiceInstanceInterface> serviceInterfaceList = serviceInstanceInterfaceRepo.findAllByServiceInstance(serviceCandidate);
					boolean interfacePolicyMatch = false;
					boolean interfaceTemplateMatch = false;
					boolean interfacePropertyMatch = false;
					for (final ServiceInstanceInterface interf : serviceInterfaceList) {
						// Interface policy
						if (Utilities.isEmpty(filters.getPolicies()) || filters.getPolicies().contains(interf.getPolicy())) {
							interfacePolicyMatch = true;
						}
						// Interface template
						if (Utilities.isEmpty(filters.getInterfaceTemplateNames()) || filters.getInterfaceTemplateNames().contains(interf.getServiceInterfaceTemplate().getName())) {
							interfaceTemplateMatch = true;
						}
						// Interface properties
						if (Utilities.isEmpty(filters.getInterfacePropertyRequirementsList())) {
							interfacePropertyMatch = true;
						} else {
							final Map<String, Object> interfaceProps = Utilities.fromJson(interf.getProperties(), new TypeReference<Map<String, Object>>() { });
							for (final MetadataRequirementDTO requirement : filters.getInterfacePropertyRequirementsList()) {
								if (MetadataRequirementsMatcher.isMetadataMatch(interfaceProps, requirement)) {
									interfacePropertyMatch = true;
									break;
								}
							}
						}

						// Cache the interface instance if matching
						if (interfacePolicyMatch && interfaceTemplateMatch && interfacePropertyMatch) {
							serviceInterfaceMap.putIfAbsent(serviceCandidate.getId(), new ArrayList<>());
							serviceInterfaceMap.get(serviceCandidate.getId()).add(interf);
						}
					}

					if (!serviceInterfaceMap.containsKey(serviceCandidate.getId())) {
						matching = false;
					}
				}

				if (matching) {
					matchingIds.add(serviceCandidate.getId());
				}
			}

			serviceInstanceRepo.deleteAllById(deleteIds);
			final Page<ServiceInstance> page = serviceInstanceRepo.findAllByIdIn(matchingIds, pagination);
			return new PageImpl<>(page.stream().map(si -> Map.entry(si, serviceInterfaceMap.get(si.getId()))).toList(), pagination, page.getTotalElements());
		}
	}

	//=================================================================================================
	// nested classes

	private enum BaseFilter {
		NONE, INSTANCE_ID, SYSTEM_NAME, SERVICE_NAME
	}
}
