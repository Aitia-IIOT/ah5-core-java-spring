package eu.arrowhead.serviceregistry.service;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.InternalServerError;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.common.service.PageService;
import eu.arrowhead.dto.DeviceListRequestDTO;
import eu.arrowhead.dto.DeviceListResponseDTO;
import eu.arrowhead.dto.DeviceQueryRequestDTO;
import eu.arrowhead.dto.DeviceRequestDTO;
import eu.arrowhead.dto.PageDTO;
import eu.arrowhead.dto.ServiceDefinitionListRequestDTO;
import eu.arrowhead.dto.ServiceDefinitionListResponseDTO;
import eu.arrowhead.dto.ServiceInterfaceTemplateListRequestDTO;
import eu.arrowhead.dto.ServiceInterfaceTemplateListResponseDTO;
import eu.arrowhead.dto.SystemListRequestDTO;
import eu.arrowhead.dto.SystemListResponseDTO;
import eu.arrowhead.dto.SystemQueryRequestDTO;
import eu.arrowhead.dto.SystemRequestDTO;
import eu.arrowhead.dto.enums.AddressType;
import eu.arrowhead.serviceregistry.jpa.entity.Device;
import eu.arrowhead.serviceregistry.jpa.entity.DeviceAddress;
import eu.arrowhead.serviceregistry.jpa.entity.ServiceDefinition;
import eu.arrowhead.serviceregistry.jpa.entity.ServiceInterfaceTemplate;
import eu.arrowhead.serviceregistry.jpa.entity.ServiceInterfaceTemplateProperty;
import eu.arrowhead.serviceregistry.jpa.entity.System;
import eu.arrowhead.serviceregistry.jpa.entity.SystemAddress;
import eu.arrowhead.serviceregistry.jpa.service.DeviceDbService;
import eu.arrowhead.serviceregistry.jpa.service.ServiceDefinitionDbService;
import eu.arrowhead.serviceregistry.jpa.service.ServiceInterfaceTemplateDbService;
import eu.arrowhead.serviceregistry.jpa.service.SystemDbService;
import eu.arrowhead.serviceregistry.service.dto.DTOConverter;
import eu.arrowhead.serviceregistry.service.validation.ManagementValidation;


@Service
public class ManagementService {

	//=================================================================================================
	// members

	@Autowired
	private ManagementValidation validator;

	@Autowired
	private PageService pageService;

	@Autowired
	private DeviceDbService deviceDbService;

	@Autowired
	private ServiceDefinitionDbService serviceDefinitionDbService;

	@Autowired
	private SystemDbService systemDbService;

	@Autowired
	private ServiceInterfaceTemplateDbService interfaceTemplateDbService;

	@Autowired
	private DTOConverter dtoConverter;

	private final Logger logger = LogManager.getLogger(this.getClass());

	//=================================================================================================
	// methods

	// DEVICES

	//-------------------------------------------------------------------------------------------------
	public DeviceListResponseDTO createDevices(final DeviceListRequestDTO dto, final String origin) {
		logger.debug("createDevices started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final List<DeviceRequestDTO> normalized = validator.validateAndNormalizeCreateDevices(dto, origin);

		try {
			final List<Entry<Device, List<DeviceAddress>>> entities = deviceDbService.createBulk(normalized);
			return dtoConverter.convertDeviceAndDeviceAddressEntriesToDTO(entities, entities.size());

		} catch (final InvalidParameterException ex) {
			throw new InvalidParameterException(ex.getMessage(), origin);

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	//-------------------------------------------------------------------------------------------------
	public DeviceListResponseDTO updateDevices(final DeviceListRequestDTO dto, final String origin) {
		logger.debug("updateDevices started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final List<DeviceRequestDTO> normalized = validator.validateAndNormalizeUpdateDevices(dto, origin);

		try {
			final List<Entry<Device, List<DeviceAddress>>> entities = deviceDbService.updateBulk(normalized);
			return dtoConverter.convertDeviceAndDeviceAddressEntriesToDTO(entities, entities.size());

		} catch (final InvalidParameterException ex) {
			throw new InvalidParameterException(ex.getMessage(), origin);

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	//-------------------------------------------------------------------------------------------------
	public DeviceListResponseDTO queryDevices(final DeviceQueryRequestDTO dto, final String origin) {
		logger.debug("queryDevices started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");
		final DeviceQueryRequestDTO normalized = validator.validateAndNormalizeQueryDevices(dto, origin);

		final PageRequest pageRequest = pageService.getPageRequest(normalized.pagination(), Direction.DESC, Device.SORTABLE_FIELDS_BY, Device.DEFAULT_SORT_FIELD, origin);

		final Page<Entry<Device, List<DeviceAddress>>> page = deviceDbService.getPage(pageRequest, normalized.deviceNames(), normalized.addresses(),
				Utilities.isEmpty(normalized.addressType()) ? null : AddressType.valueOf(normalized.addressType()), normalized.metadataRequirementList());
		return dtoConverter.convertDeviceAndDeviceAddressEntriesToDTO(page, page.getTotalElements());
	}

	//-------------------------------------------------------------------------------------------------
	public void removeDevices(final List<String> names, final String origin) {
		logger.debug("removeDevices started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");
		try {
			final List<String> normalized = validator.validateAndNormalizeRemoveDevices(names, origin);
			deviceDbService.deleteByNameList(normalized);

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		} catch (final InvalidParameterException ex) {
			throw new InvalidParameterException(ex.getMessage(), origin);
		}
	}

	// SERVICES DEFINITIONS

	//-------------------------------------------------------------------------------------------------
	public ServiceDefinitionListResponseDTO getServiceDefinitions(final PageDTO dto, final String origin) {
		logger.debug("getServiceDefinitions started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		validator.validateQueryServiceDefinitions(dto, origin);
		final PageRequest pageRequest = pageService.getPageRequest(dto, Direction.DESC, ServiceDefinition.SORTABLE_FIELDS_BY, ServiceDefinition.DEFAULT_SORT_FIELD, origin);

		try {
			final Page<ServiceDefinition> entities = serviceDefinitionDbService.getPage(pageRequest);
			return dtoConverter.convertServiceDefinitionEntityPageToDTO(entities);
		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	//-------------------------------------------------------------------------------------------------
	public ServiceDefinitionListResponseDTO createServiceDefinitions(final ServiceDefinitionListRequestDTO dto, final String origin) {
		logger.debug("createServiceDefinitions started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final List<String> normalized = validator.validateAndNormalizeCreateServiceDefinitions(dto, origin);

		try {
			final List<ServiceDefinition> entities = serviceDefinitionDbService.createBulk(normalized);
			return dtoConverter.convertServiceDefinitionEntityListToDTO(entities);
		} catch (final InvalidParameterException ex) {
			throw new InvalidParameterException(ex.getMessage(), origin);

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	//-------------------------------------------------------------------------------------------------
	public void removeServiceDefinitions(final List<String> names, final String origin) {
		logger.debug("removeServiceDefinitions started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final List<String> normalized = validator.validateAndNormalizeRemoveServiceDefinitions(names, origin);

		try {
			serviceDefinitionDbService.removeBulk(normalized);
		} catch (final InvalidParameterException ex) {
			throw new InvalidParameterException(ex.getMessage(), origin);

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	// SYSTEMS

	//-------------------------------------------------------------------------------------------------
	public SystemListResponseDTO createSystems(final SystemListRequestDTO dto, final String origin) {
		logger.debug("createSystems started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final List<SystemRequestDTO> normalized = validator.validateAndNormalizeCreateSystems(dto, origin);

		try {

			final List<Triple<System, List<SystemAddress>, Entry<Device, List<DeviceAddress>>>> entities = systemDbService.createBulk(normalized);
			return dtoConverter.convertSystemTripletListToDTO(entities);

		} catch (final InvalidParameterException ex) {

			throw new InvalidParameterException(ex.getMessage(), origin);

		} catch (final InternalServerError ex) {

			throw new InternalServerError(ex.getMessage(), origin);

		}

	}

	//-------------------------------------------------------------------------------------------------
	public SystemListResponseDTO querySystems(final SystemQueryRequestDTO dto, final boolean verbose, final String origin) {
		logger.debug("querySystems started, verbose = {}", verbose);
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final SystemQueryRequestDTO normalized = validator.validateAndNormalizeQuerySystems(dto, origin);

		try {
			final PageRequest pageRequest = pageService.getPageRequest(normalized.pagination(), Direction.DESC, System.SORTABLE_FIELDS_BY, System.DEFAULT_SORT_FIELD, origin);
			final Page<Triple<System, List<SystemAddress>, Entry<Device, List<DeviceAddress>>>> page = systemDbService.getPageByFilters(
					pageRequest,
					normalized.systemNames(),
					normalized.addresses(),
					Utilities.isEmpty(normalized.addressType()) ? null : AddressType.valueOf(normalized.addressType()),
					normalized.metadataRequirementList(),
					normalized.versions(),
					normalized.deviceNames());

			final SystemListResponseDTO result = dtoConverter.convertSystemTripletPageToDTO(page);

			if (!verbose) {
				return dtoConverter.convertSystemListResponseDtoToTerse(result);
			}

			return result;
		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	//-------------------------------------------------------------------------------------------------
	public SystemListResponseDTO updateSystems(final SystemListRequestDTO dto, final String origin) {
		logger.debug("updateSystems started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final List<SystemRequestDTO> normalized = validator.validateAndNormalizeUpdateSystems(dto, origin);

		try {
			final List<Triple<System, List<SystemAddress>, Entry<Device, List<DeviceAddress>>>> updatedEntities = systemDbService.updateBulk(normalized);
			return dtoConverter.convertSystemTripletListToDTO(updatedEntities);

		} catch (final InvalidParameterException ex) {
			throw new InvalidParameterException(ex.getMessage(), origin);

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	//-------------------------------------------------------------------------------------------------
	public void removeSystems(final List<String> names, final String origin) {
		logger.debug("removeSystems started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final List<String> normalizedNames = validator.validateAndNormalizeRemoveSystems(names, origin);

		try {
			systemDbService.deleteByNameList(normalizedNames);
		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	// INTERFACE TEMPLATES

	//-------------------------------------------------------------------------------------------------
	public ServiceInterfaceTemplateListResponseDTO createInterfaceTemplates(final ServiceInterfaceTemplateListRequestDTO dto, final String origin) {
		logger.debug("createInterfaceTemplates started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final ServiceInterfaceTemplateListRequestDTO normalized = validator.validateAndNormalizeCreateInterfaceTemplates(dto, origin);

		try {
			final Map<ServiceInterfaceTemplate, List<ServiceInterfaceTemplateProperty>> entries = interfaceTemplateDbService.createBulk(dto.interfaceTemplates());
			// TODO dtoConvert
			return null;

		} catch (final InvalidParameterException ex) {
			throw new InvalidParameterException(ex.getMessage(), origin);

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	//-------------------------------------------------------------------------------------------------
	public void removeInterfaceTemplates(final List<String> names, final String origin) {
		logger.debug("removeInterfaceTemplates started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		final List<String> normalizedNames = validator.validateAndNormalizeRemoveInterfaceTemplates(names, origin);

		try {
			interfaceTemplateDbService.deleteByTemplateNameList(normalizedNames);
		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}
}
