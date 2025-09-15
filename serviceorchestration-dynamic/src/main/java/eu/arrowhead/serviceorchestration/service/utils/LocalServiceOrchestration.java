/*******************************************************************************
 *
 * Copyright (c) 2025 AITIA
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 *
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  	AITIA - implementation
 *  	Arrowhead Consortia - conceptualization
 *
 *******************************************************************************/
package eu.arrowhead.serviceorchestration.service.utils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.Defaults;
import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.AuthException;
import eu.arrowhead.common.exception.ForbiddenException;
import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.common.service.util.ServiceInterfaceAddressPropertyProcessor;
import eu.arrowhead.common.service.validation.MetadataRequirementsMatcher;
import eu.arrowhead.common.service.validation.meta.MetaOps;
import eu.arrowhead.common.service.validation.meta.MetadataRequirementExpression;
import eu.arrowhead.common.service.validation.meta.MetadataRequirementTokenizer;
import eu.arrowhead.dto.AuthorizationTokenGenerationMgmtListRequestDTO;
import eu.arrowhead.dto.AuthorizationTokenGenerationMgmtRequestDTO;
import eu.arrowhead.dto.AuthorizationTokenGenerationResponseDTO;
import eu.arrowhead.dto.AuthorizationTokenMgmtListResponseDTO;
import eu.arrowhead.dto.AuthorizationTokenResponseDTO;
import eu.arrowhead.dto.AuthorizationVerifyListRequestDTO;
import eu.arrowhead.dto.AuthorizationVerifyListResponseDTO;
import eu.arrowhead.dto.AuthorizationVerifyRequestDTO;
import eu.arrowhead.dto.BlacklistEntryDTO;
import eu.arrowhead.dto.BlacklistEntryListResponseDTO;
import eu.arrowhead.dto.BlacklistQueryRequestDTO;
import eu.arrowhead.dto.MetadataRequirementDTO;
import eu.arrowhead.dto.OrchestrationResponseDTO;
import eu.arrowhead.dto.OrchestrationResultDTO;
import eu.arrowhead.dto.PageDTO;
import eu.arrowhead.dto.ServiceInstanceInterfaceResponseDTO;
import eu.arrowhead.dto.ServiceInstanceListResponseDTO;
import eu.arrowhead.dto.ServiceInstanceLookupRequestDTO;
import eu.arrowhead.dto.TranslationBridgeCandidateDTO;
import eu.arrowhead.dto.TranslationDiscoveryMgmtRequestDTO;
import eu.arrowhead.dto.TranslationDiscoveryResponseDTO;
import eu.arrowhead.dto.TranslationNegotiationMgmtRequestDTO;
import eu.arrowhead.dto.TranslationNegotiationResponseDTO;
import eu.arrowhead.dto.enums.AuthorizationTargetType;
import eu.arrowhead.dto.enums.AuthorizationTokenType;
import eu.arrowhead.dto.enums.OrchestrationFlag;
import eu.arrowhead.dto.enums.QoSEvaulationType;
import eu.arrowhead.dto.enums.ServiceInterfacePolicy;
import eu.arrowhead.dto.enums.TranslationDiscoveryFlag;
import eu.arrowhead.serviceorchestration.DynamicServiceOrchestrationConstants;
import eu.arrowhead.serviceorchestration.DynamicServiceOrchestrationSystemInfo;
import eu.arrowhead.serviceorchestration.jpa.entity.OrchestrationLock;
import eu.arrowhead.serviceorchestration.jpa.service.OrchestrationJobDbService;
import eu.arrowhead.serviceorchestration.jpa.service.OrchestrationLockDbService;
import eu.arrowhead.serviceorchestration.service.enums.OrchestrationJobStatus;
import eu.arrowhead.serviceorchestration.service.model.OrchestrationCandidate;
import eu.arrowhead.serviceorchestration.service.model.OrchestrationForm;
import eu.arrowhead.serviceorchestration.service.utils.matchmaker.ServiceInstanceMatchmaker;
import jakarta.annotation.Resource;

@Service
public class LocalServiceOrchestration {

	//=================================================================================================
	// members

	@Autowired
	private DynamicServiceOrchestrationSystemInfo sysInfo;

	@Autowired
	private InterCloudServiceOrchestration interCloudOrch;

	@Autowired
	private OrchestrationJobDbService orchJobDbService;

	@Autowired
	private OrchestrationLockDbService orchLockDbService;

	@Autowired
	private ArrowheadHttpService ahHttpService;

	@Resource(name = DynamicServiceOrchestrationConstants.SERVICE_INSTANCE_MATCHMAKER)
	private ServiceInstanceMatchmaker matchmaker;

	@Autowired
	private ServiceInterfaceAddressPropertyProcessor interfaceAddressPropertyProcessor;

	private static final int TEMPORARY_LOCK_DURATION = 60; // sec

	private static final int RESERVATION_BUFFER = 10; // sec

	private final Logger logger = LogManager.getLogger(this.getClass());

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@SuppressWarnings("checkstyle:MethodLengthCheck")
	public OrchestrationResponseDTO doLocalServiceOrchestration(final UUID jobId, final OrchestrationForm form) {
		logger.debug("doLocalServiceOrchestration started...");

		orchJobDbService.setStatus(jobId, OrchestrationJobStatus.IN_PROGRESS, null);

		try {
			final Set<String> warnings = new HashSet<>();

			// Discovering the service
			final boolean translationAllowed = form.getFlag(OrchestrationFlag.ALLOW_TRANSLATION) && sysInfo.isTranslationEnabled();
			List<OrchestrationCandidate> candidates = serviceDiscovery(form, translationAllowed, form.getFlag(OrchestrationFlag.ONLY_PREFERRED));
			candidates = filterOutLockedOnes(jobId, candidates);

			if (Utilities.isEmpty(candidates)) {
				return doInterCloudOrReturn(jobId, form);
			}

			// Dealing with exclusivity
			if (form.exclusivityIsPreferred()) {
				markExclusivityIfFeasible(candidates);
			}

			if (form.getFlag(OrchestrationFlag.ONLY_EXCLUSIVE)) {
				candidates = filterOutWhereExclusivityIsNotPossible(candidates);

				if (Utilities.isEmpty(candidates)) {
					return doInterCloudOrReturn(jobId, form);
				}

				if (form.addFlag(OrchestrationFlag.MATCHMAKING, true)) {
					warnings.add(DynamicServiceOrchestrationConstants.ORCH_WARN_AUTO_MATCHMAKING);
				}
			}

			// Blacklist cross-check
			if (sysInfo.isBlacklistEnabled()) {
				candidates = filterOutBlacklistedOnes(candidates);
			}

			if (Utilities.isEmpty(candidates)) {
				return doInterCloudOrReturn(jobId, form);
			}

			// Authorization cross-check
			if (sysInfo.isAuthorizationEnabled()) {
				candidates = filterOutUnauthorizedOnes(form, candidates);
			}

			if (Utilities.isEmpty(candidates)) {
				return doInterCloudOrReturn(jobId, form);
			}

			// Temporary lock if required and possible
			if (form.exclusivityIsPreferred()) {
				candidates = filterOutLockedOnesAndTemporaryLockIfCanBeExclusive(jobId, form.getRequesterSystemName(), candidates);
			} else {
				candidates = filterOutLockedOnes(jobId, candidates);
			}

			if (Utilities.isEmpty(candidates)) {
				return doInterCloudOrReturn(jobId, form);
			}

			// Collect matching interfaces and mark if translation is necessary
			assortInterfacesAndMarkIfNonNative(form, candidates, translationAllowed);

			// QoS cross-check
			if (form.hasQoSRequirements()) {
				if (!sysInfo.isQoSEnabled()) {
					warnings.add(DynamicServiceOrchestrationConstants.ORCH_WARN_QOS_NOT_ENABLED);
					releaseTemporaryLockIfItWasLocked(jobId, candidates);
					orchJobDbService.setStatus(jobId, OrchestrationJobStatus.DONE, "No results were found");

					return convertToOrchestrationResponse(List.of(), warnings);
				}
				candidates = doQoSCompliance(candidates);

				if (Utilities.isEmpty(candidates)) {
					return doInterCloudOrReturn(jobId, form);
				}
			}

			// Deal with translations
			String translationBirdgeId = null;
			if (translationAllowed) {
				if (checkIfHasNativeOnes(candidates)) {
					candidates = filterOutNonNativeOnes(candidates);
				} else {
					final Pair<String, List<OrchestrationCandidate>> translatable = filterOutNotTranslatableOnesAndChooseTranslatableInterfaces(form, candidates); // translation discovery
					translationBirdgeId = translatable.getFirst();
					candidates = translatable.getSecond();
					if (Utilities.isEmpty(candidates)) {
						return doInterCloudOrReturn(jobId, form);
					}
					if (form.addFlag(OrchestrationFlag.MATCHMAKING, true)) {
						warnings.add(DynamicServiceOrchestrationConstants.ORCH_WARN_AUTO_MATCHMAKING);
					}
				}
			}

			// Dealing with preferences
			if (form.exclusivityIsPreferred() && !form.getFlag(OrchestrationFlag.ONLY_EXCLUSIVE)) {
				if (containsReservable(candidates)) {
					candidates = filterOutWhereExclusivityIsNotPossible(candidates);
					if (form.addFlag(OrchestrationFlag.MATCHMAKING, true)) {
						warnings.add(DynamicServiceOrchestrationConstants.ORCH_WARN_AUTO_MATCHMAKING);
					}
				} else {
					warnings.add(DynamicServiceOrchestrationConstants.ORCH_WARN_NOT_EXCLUSIVE);
				}
			}

			if (form.hasPreferredProviders() && !form.getFlag(OrchestrationFlag.ONLY_PREFERRED)) {
				if (searchForPreferredProviders(form, candidates)) {
					candidates = filterOutNonPreferredProviders(candidates);
				}
			}

			if (Utilities.isEmpty(candidates)) {
				return doInterCloudOrReturn(jobId, form);
			}

			// Matchmaking if required
			if (form.getFlag(OrchestrationFlag.MATCHMAKING)) {
				final OrchestrationCandidate match = matchmaking(form, candidates);
				if (form.exclusivityIsPreferred()) {
					reserve(jobId, form.getRequesterSystemName(), match, form.getExclusivityDuration());
					final String matchInstanceId = match.getServiceInstance().instanceId();
					releaseTemporaryLockIfItWasLocked(jobId, candidates.stream().filter(c -> !c.getServiceInstance().instanceId().equals(matchInstanceId)).collect(Collectors.toList()));
					if (form.getExclusivityDuration() > match.getExclusivityDuration()) {
						warnings.add(DynamicServiceOrchestrationConstants.ORCH_WARN_PART_TIME_EXCLUSIVITY);
					}
				}

				candidates.clear();
				candidates.add(match);
			}

			// Obtain Authorization tokens when required
			if (sysInfo.isAuthorizationEnabled() && Utilities.isEmpty(translationBirdgeId)) {
				obtainAuthorizationTokensIfRequired(form, candidates);
			}

			// Create translation bridge if necessary
			if (!Utilities.isEmpty(translationBirdgeId)) {
				Assert.isTrue(form.getFlag(OrchestrationFlag.MATCHMAKING), "There was no matchmaking, while translation bridge should have been initiated.");
				buildTranslationBridge(translationBirdgeId, form.getOperations().getFirst(), candidates.getFirst());
				warnings.add(DynamicServiceOrchestrationConstants.ORCH_WARN_FORCED_INTERFACE_SECURITY_POLICY);
			}

			final OrchestrationResponseDTO result = convertToOrchestrationResponse(candidates, warnings);
			orchJobDbService.setStatus(jobId, OrchestrationJobStatus.DONE, candidates.size() + " local result");

			return result;
		} catch (final Exception ex) {
			orchJobDbService.setStatus(jobId, OrchestrationJobStatus.ERROR, ex.getMessage());
			throw ex;
		}
	}

	//=================================================================================================
	// assistant methods

	//-------------------------------------------------------------------------------------------------
	private List<OrchestrationCandidate> serviceDiscovery(final OrchestrationForm form, final boolean withoutInterface, final boolean onlyPreferred) {
		logger.debug("serviceDiscovery started...");

		final ServiceInstanceLookupRequestDTO lookupDTO = new ServiceInstanceLookupRequestDTO.Builder()
				.serviceDefinitionName(form.getServiceDefinition())
				.versions(form.getVersions())
				.alivesAt(form.getAlivesAt())
				.metadataRequirementsList(form.getMetadataRequirements())
				.policies(form.getSecurityPolicies())
				.interfaceTemplateNames(withoutInterface ? null : form.getInterfaceTemplateNames())
				.interfacePropertyRequirementsList(withoutInterface ? null : form.getInterfacePropertyRequirements())
				.addressTypes(withoutInterface ? null : form.getInterfaceAddressTypes())
				.providerNames(onlyPreferred ? form.getPreferredProviders() : null)
				.build();

		final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>(Map.of(Constants.VERBOSE, List.of(Boolean.FALSE.toString())));
		final ServiceInstanceListResponseDTO response = ahHttpService.consumeService(
				Constants.SERVICE_DEF_SERVICE_DISCOVERY,
				Constants.SERVICE_OP_LOOKUP,
				Constants.SYS_NAME_SERVICE_REGISTRY,
				ServiceInstanceListResponseDTO.class,
				lookupDTO,
				queryParams);

		if (Utilities.isEmpty(response.entries())) {
			return List.of();
		}

		return response.entries()
				.stream()
				.map(instance -> new OrchestrationCandidate(instance, true, onlyPreferred))
				.collect(Collectors.toList());
	}

	//-------------------------------------------------------------------------------------------------
	private List<OrchestrationCandidate> filterOutLockedOnes(final UUID jobId, final List<OrchestrationCandidate> candidates) {
		logger.debug("filterOutLockedOnes started...");

		if (Utilities.isEmpty(candidates)) {
			return candidates;
		}

		synchronized (DynamicServiceOrchestrationConstants.SYNC_LOCK_ORCH_LOCK) {
			final String jobIdStr = jobId.toString();
			final List<OrchestrationLock> lockRecords = orchLockDbService.getByServiceInstanceId(candidates.stream().map(c -> c.getServiceInstance().instanceId()).collect(Collectors.toList()));

			final ZonedDateTime now = Utilities.utcNow();
			final Set<Long> expiredLocks = new HashSet<>();
			final Set<String> lockedServiceInstanceIds = new HashSet<>();

			for (final OrchestrationLock lockRecord : lockRecords) {
				if (!Utilities.isEmpty(lockRecord.getOrchestrationJobId()) && lockRecord.getOrchestrationJobId().equals(jobIdStr)) {
					// lock belongs to this session (in theory it doesn't happen)
					continue;
				} else if (lockRecord.getExpiresAt() != null && lockRecord.getExpiresAt().isBefore(now)) {
					expiredLocks.add(lockRecord.getId());
				} else {
					lockedServiceInstanceIds.add(lockRecord.getServiceInstanceId());
				}
			}

			if (!Utilities.isEmpty(expiredLocks)) {
				orchLockDbService.deleteInBatch(expiredLocks);
			}

			return candidates
					.stream()
					.filter(c -> !lockedServiceInstanceIds.contains(c.getServiceInstance().instanceId()))
					.collect(Collectors.toList());
		}
	}

	//-------------------------------------------------------------------------------------------------
	private List<OrchestrationCandidate> filterOutLockedOnesAndTemporaryLockIfCanBeExclusive(final UUID jobId, final String consumerSystem, final List<OrchestrationCandidate> candidates) {
		logger.debug("filterOutLockedOnesAndTemporaryLockIfCanBeExclusive started...");

		if (Utilities.isEmpty(candidates)) {
			return candidates;
		}

		synchronized (DynamicServiceOrchestrationConstants.SYNC_LOCK_ORCH_LOCK) {
			final List<OrchestrationCandidate> freeCandidates = filterOutLockedOnes(jobId, candidates);
			if (!Utilities.isEmpty(freeCandidates)) {
				final String jobIdStr = jobId.toString();
				final ZonedDateTime tempLockExpiresAt = Utilities.utcNow().plusSeconds(TEMPORARY_LOCK_DURATION);

				final List<OrchestrationLock> toLock = new ArrayList<>(freeCandidates.size());
				for (final OrchestrationCandidate candidate : freeCandidates) {
					if (candidate.canBeExclusive()) {
						candidate.setLocked(true);
						toLock.add(new OrchestrationLock(jobIdStr, candidate.getServiceInstance().instanceId(), consumerSystem, tempLockExpiresAt, true));
					}
				}

				if (!Utilities.isEmpty(toLock)) {
					orchLockDbService.create(toLock);
				}
			}
			return freeCandidates;
		}
	}

	//-------------------------------------------------------------------------------------------------
	private void markExclusivityIfFeasible(final List<OrchestrationCandidate> candidates) {
		logger.debug("markExclusivityIfFeasible started...");

		for (final OrchestrationCandidate candidate : candidates) {
			final Object allowedExclusivityDurationObj = candidate.getServiceInstance().metadata() == null ? null : candidate.getServiceInstance().metadata().get(Constants.METADATA_KEY_ALLOW_EXCLUSIVITY);
			if (allowedExclusivityDurationObj != null) {
				try {
					final int allowedExclusivityDuration = Utilities.parseToInt(allowedExclusivityDurationObj);
					candidate.setCanBeExclusive(allowedExclusivityDuration > 0);
					candidate.setExclusivityDuration(allowedExclusivityDuration);

				} catch (final NumberFormatException ex) {
					logger.warn(Constants.METADATA_KEY_ALLOW_EXCLUSIVITY + " metadata is not an integer. Service instance id: " + candidate.getServiceInstance().instanceId());
				}
			}
		}
	}

	//-------------------------------------------------------------------------------------------------
	private List<OrchestrationCandidate> filterOutWhereExclusivityIsNotPossible(final List<OrchestrationCandidate> candidates) {
		logger.debug("filterOutWhereExclusivityIsNotPossible started...");

		return candidates
				.stream()
				.filter(c -> c.canBeExclusive())
				.collect(Collectors.toList());
	}

	//-------------------------------------------------------------------------------------------------
	private boolean containsReservable(final List<OrchestrationCandidate> candidates) {
		logger.debug("containsReservable started...");

		for (final OrchestrationCandidate candidate : candidates) {
			if (candidate.canBeExclusive()) {
				return true;
			}
		}

		return false;
	}

	//-------------------------------------------------------------------------------------------------
	private List<OrchestrationCandidate> filterOutBlacklistedOnes(final List<OrchestrationCandidate> candidates) {
		logger.debug("filterOutBlacklistedOnes started...");

		final List<String> systemNames = candidates.stream().map(c -> c.getServiceInstance().provider().name()).filter(sysName -> !sysInfo.getBlacklistCheckExcludeList().contains(sysName)).toList();
		try {

			boolean hasMorePage = false;
			int pageNumber = 0;
			Integer pageSize = null;
			final List<BlacklistEntryDTO> blacklistEntries = new ArrayList<BlacklistEntryDTO>();
			do {
				final BlacklistEntryListResponseDTO response = ahHttpService.consumeService(
						Constants.SERVICE_DEF_BLACKLIST_MANAGEMENT,
						Constants.SERVICE_OP_BLACKLIST_QUERY,
						Constants.SYS_NAME_BLACKLIST,
						BlacklistEntryListResponseDTO.class,
						new BlacklistQueryRequestDTO(new PageDTO(pageNumber == 0 ? null : pageNumber, pageSize, null, null), systemNames, null, null, null, null, Utilities.convertZonedDateTimeToUTCString(Utilities.utcNow())));

				blacklistEntries.addAll(response.entries());
				hasMorePage = blacklistEntries.size() < response.count();
				pageNumber = hasMorePage ? pageNumber + 1 : pageNumber;
				pageSize = pageSize == null ? response.entries().size() : pageSize;

			} while (hasMorePage);

			final List<OrchestrationCandidate> result = candidates.stream().filter(candidate -> {
				boolean isBlacklisted = false;
				for (final BlacklistEntryDTO blDTO : blacklistEntries) {
					if (candidate.getServiceInstance().provider().name().equals(blDTO.systemName())) {
						isBlacklisted = true;
						break;
					}
				}
				return !isBlacklisted;
			}).toList();

			return result;

		} catch (final ForbiddenException | AuthException ex) {
			throw ex;
		} catch (final ArrowheadException ex) {
			logger.error("Blacklist server is not available during the orchestration process");
			if (sysInfo.isBlacklistForced()) {
				logger.error("All the provider candidate has been filtered out, because blacklist filter is forced");
				return List.of();
			} else {
				logger.error("All the provider candidate has been passed, because blacklist filter is not forced");
				return candidates;
			}
		}
	}

	//-------------------------------------------------------------------------------------------------
	private List<OrchestrationCandidate> filterOutUnauthorizedOnes(final OrchestrationForm form, final List<OrchestrationCandidate> candidates) {
		logger.debug("filterOutUnauthorizedOnes started...");

		final AuthorizationVerifyListRequestDTO payload = calculateVerifyPayload(form.getTargetSystemName(), form.getOperations(), candidates);
		final AuthorizationVerifyListResponseDTO response = ahHttpService.consumeService(
				Constants.SERVICE_DEF_AUTHORIZATION_MANAGEMENT,
				Constants.SERVICE_OP_AUTHORIZATION_CHECK_POLICIES,
				Constants.SYS_NAME_CONSUMER_AUTHORIZATION,
				AuthorizationVerifyListResponseDTO.class,
				payload);

		if (response.entries().isEmpty()) {
			return List.of();
		}

		final List<OrchestrationCandidate> result = new ArrayList<>();
		candidates.forEach(c -> {
			final boolean denied = response
					.entries()
					.stream()
					.anyMatch(e -> e.provider().equals(c.getServiceInstance().provider().name()) && !e.granted());

			if (!denied) {
				// have access to all specified operations (or all operations if nothing is specified)
				result.add(c);
			}
		});

		return result;
	}

	//-------------------------------------------------------------------------------------------------
	private AuthorizationVerifyListRequestDTO calculateVerifyPayload(final String consumerName, final List<String> operations, final List<OrchestrationCandidate> candidates) {
		logger.debug("calculateVerifyPayload started...");

		final int capacity = Utilities.isEmpty(operations) ? candidates.size() : candidates.size() * operations.size();
		final List<AuthorizationVerifyRequestDTO> list = new ArrayList<>(capacity);
		candidates.forEach(c -> {
			if (Utilities.isEmpty(operations)) {
				list.add(new AuthorizationVerifyRequestDTO(
						c.getServiceInstance().provider().name(),
						consumerName,
						null, // local cloud
						AuthorizationTargetType.SERVICE_DEF.name(),
						c.getServiceInstance().serviceDefinition().name(),
						null)); // no scope
			} else {
				for (final String operation : operations) {
					list.add(new AuthorizationVerifyRequestDTO(
							c.getServiceInstance().provider().name(),
							consumerName,
							null, // local cloud
							AuthorizationTargetType.SERVICE_DEF.name(),
							c.getServiceInstance().serviceDefinition().name(),
							operation));
				}
			}
		});

		return new AuthorizationVerifyListRequestDTO(list);
	}

	//-------------------------------------------------------------------------------------------------
	private void assortInterfacesAndMarkIfNonNative(final OrchestrationForm form, final List<OrchestrationCandidate> candidates, final boolean considerAddressTypes) {
		logger.debug("assortInterfacesAndMarkIfNonNative started...");

		if (Utilities.isEmpty(form.getInterfaceTemplateNames())
				&& Utilities.isEmpty(form.getInterfacePropertyRequirements())
				&& Utilities.isEmpty(form.getInterfaceAddressTypes())) {
			for (final OrchestrationCandidate candidate : candidates) {
				candidate.addMatchingInterfaces(candidate.getServiceInstance().interfaces());
			}

			return;
		}

		for (final OrchestrationCandidate candidate : candidates) {
			for (final ServiceInstanceInterfaceResponseDTO offeredInterface : candidate.getServiceInstance().interfaces()) {
				boolean isMatchingInterface = true;

				// Checking interface template names
				if (!Utilities.isEmpty(form.getInterfaceTemplateNames())
						&& !form.getInterfaceTemplateNames().contains(offeredInterface.templateName())) {
					isMatchingInterface = false;

					// Checking interface properties
				} else if (!Utilities.isEmpty(form.getInterfacePropertyRequirements())) {
					for (final MetadataRequirementDTO interfacePropertyRequirement : form.getInterfacePropertyRequirements()) {
						if (!MetadataRequirementsMatcher.isMetadataMatch(offeredInterface.properties(), interfacePropertyRequirement)) {
							isMatchingInterface = false;
							break;
						}
					}
				}

				// Checking address types
				if (considerAddressTypes
						&& isMatchingInterface
						&& !Utilities.isEmpty(form.getInterfaceAddressTypes())) {
					isMatchingInterface = interfaceAddressPropertyProcessor.filterOnAddressTypes(offeredInterface.properties(), form.getInterfaceAddressTypes());
				}

				if (isMatchingInterface) {
					candidate.addMatchingInterface(offeredInterface);
				}
			}

			candidate.setNonNative(Utilities.isEmpty(candidate.getMatchingInterfaces()));
		}
	}

	//-------------------------------------------------------------------------------------------------
	private QoSEvaulationType getQoSEvaulationType(final OrchestrationForm form) {
		logger.debug("getQoSEvaulationType started...");

		// TODO

		logger.warn("QoS support is not implemented yet");
		return QoSEvaulationType.FILTERING;
	}

	//-------------------------------------------------------------------------------------------------
	private List<OrchestrationCandidate> doQoSCompliance(final List<OrchestrationCandidate> candidates) {
		logger.debug("doQoSCompliance started...");

		// TODO implement when QoS Evaluator is ready
		// TODO let QoS evaluator know that translation is necessary or not

		logger.warn("QoS crosschek is not implemented yet");
		return candidates;
	}

	//-------------------------------------------------------------------------------------------------
	private boolean checkIfHasNativeOnes(final List<OrchestrationCandidate> candidates) {
		logger.debug("checkIfHasNativeOnes started...");

		for (final OrchestrationCandidate candidate : candidates) {
			if (!candidate.isNonNative()) {
				return true;
			}
		}

		return false;
	}

	//-------------------------------------------------------------------------------------------------
	private List<OrchestrationCandidate> filterOutNonNativeOnes(final List<OrchestrationCandidate> candidates) {
		logger.debug("filterOutNonNativeOnes started...");

		return candidates
				.stream()
				.filter(c -> !c.isNonNative())
				.collect(Collectors.toList());
	}

	//-------------------------------------------------------------------------------------------------
	private Pair<String, List<OrchestrationCandidate>> filterOutNotTranslatableOnesAndChooseTranslatableInterfaces(final OrchestrationForm form, final List<OrchestrationCandidate> candidates) {
		logger.debug("filterOutNotTranslatableOnesAndChooseTranslatableInterfaces started...");
		Assert.isTrue(!(Utilities.isEmpty(form.getOperations()) || form.getOperations().size() != 1),
				"There should be exactly one operation requested for performing translationDiscovery, but there is more/all."); // responsibility of OrchestrationFromContextValidation
		Assert.isTrue(!Utilities.isEmpty(form.getInterfaceTemplateNames()),
				"There should be at least one interface name requested for performing translationDiscovery, but there is zero."); // responsibility of OrchestrationFromContextValidation

		final String operation = form.getOperations().getFirst();
		final String inputDataModelIdKey = "dataModels" + Constants.DOT + operation + Constants.DOT + "input";
		final String outputDataModelIdKey = "dataModels" + Constants.DOT + operation + Constants.DOT + "output";
		String inputDataModelId = null;
		String outputDataModelId = null;
		if (!Utilities.isEmpty(form.getInterfacePropertyRequirements())) {
			for (final MetadataRequirementDTO requirementBundle : form.getInterfacePropertyRequirements()) {
				if (inputDataModelId != null && outputDataModelId != null) {
					break;
				}
				for (final MetadataRequirementExpression requirement : MetadataRequirementTokenizer.parseRequirements(requirementBundle)) {
					if (inputDataModelId == null && requirement.operation() == MetaOps.EQUALS && requirement.keyPath().equals(inputDataModelIdKey)) {
						inputDataModelId = (String) requirement.value();
					}
					if (outputDataModelId == null && requirement.operation() == MetaOps.EQUALS && requirement.keyPath().equals(outputDataModelIdKey)) {
						outputDataModelId = (String) requirement.value();
					}
				}
			}
		}

		final TranslationDiscoveryMgmtRequestDTO discoveryRequest = new TranslationDiscoveryMgmtRequestDTO(
				candidates.stream().map(c -> c.getServiceInstance()).toList(),
				form.getTargetSystemName(),
				operation,
				form.getInterfaceTemplateNames(),
				inputDataModelId,
				outputDataModelId,
				Map.of(TranslationDiscoveryFlag.CONSUMER_BLACKLIST_CHECK.toString(), false,
						TranslationDiscoveryFlag.CANDIDATES_BLACKLIST_CHECK.toString(), false,
						TranslationDiscoveryFlag.CANDIDATES_AUTH_CHECK.toString(), false,
						TranslationDiscoveryFlag.TRANSLATORS_BLACKLIST_CHECK.toString(), sysInfo.isBlacklistEnabled(),
						TranslationDiscoveryFlag.TRANSLATORS_AUTH_CHECK.toString(), sysInfo.isAuthorizationEnabled()));

		final TranslationDiscoveryResponseDTO discoveryResults = ahHttpService.consumeService(
				Constants.SERVICE_DEF_TRANSLATION_BRIDGE_MANAGEMENT,
				Constants.SERVICE_OP_DISCOVERY,
				Constants.SYS_NAME_TRANSLATIONMANAGER,
				TranslationDiscoveryResponseDTO.class,
				discoveryRequest);

		if (Utilities.isEmpty(discoveryResults.candidates())) {
			return Pair.of("", List.of());
		}

		Assert.isTrue(!(Utilities.isEmpty(discoveryResults.bridgeId())), "No bridgeId has been provided by " + Constants.SYS_NAME_TRANSLATIONMANAGER);

		final List<OrchestrationCandidate> translatableCandidates = new ArrayList<>();
		for (final OrchestrationCandidate candidate : candidates) {
			for (final ServiceInstanceInterfaceResponseDTO candidateInterface : candidate.getServiceInstance().interfaces()) {
				if (!Utilities.isEmpty(candidate.getMatchingInterfaces())) {
					break;
				}
				for (final TranslationBridgeCandidateDTO discovered : discoveryResults.candidates()) {
					if (!candidate.getServiceInstance().instanceId().equals(discovered.serviceInstanceId())) {
						continue;
					} else if (candidateInterface.templateName().equals(discovered.interfaceTemplateName())) {
						candidate.addMatchingInterface(candidateInterface);
						break;
					}
				}
			}
			if (!Utilities.isEmpty(candidate.getMatchingInterfaces())) {
				translatableCandidates.add(candidate);
			}
		}

		return Pair.of(discoveryResults.bridgeId(), translatableCandidates);
	}

	//-------------------------------------------------------------------------------------------------
	private boolean searchForPreferredProviders(final OrchestrationForm form, final List<OrchestrationCandidate> candidates) {
		logger.debug("searchForPreferredProviders started...");

		boolean hasPreferred = false;
		for (final OrchestrationCandidate candidate : candidates) {
			if (form.getPreferredProviders().contains(candidate.getServiceInstance().provider().name())) {
				hasPreferred = true;
				candidate.setPreferred(true);
			}
		}

		return hasPreferred;
	}

	//-------------------------------------------------------------------------------------------------
	private List<OrchestrationCandidate> filterOutNonPreferredProviders(final List<OrchestrationCandidate> candidates) {
		logger.debug("filterOutNonPreferredProviders started...");

		return candidates
				.stream()
				.filter(c -> c.isPreferred())
				.collect(Collectors.toList());
	}

	//-------------------------------------------------------------------------------------------------
	private OrchestrationCandidate matchmaking(final OrchestrationForm form, final List<OrchestrationCandidate> candidates) {
		logger.debug("matchmaking started...");

		if (form.hasQoSRequirements() && getQoSEvaulationType(form) == QoSEvaulationType.RANKING) {
			return candidates.getFirst();
		}

		return matchmaker.doMatchmaking(form, candidates);
	}

	//-------------------------------------------------------------------------------------------------
	private void reserve(final UUID jobId, final String consumerSystem, final OrchestrationCandidate candidate, final int duration) {
		logger.debug("reserve started...");

		if (!candidate.canBeExclusive()) {
			return;
		}

		synchronized (DynamicServiceOrchestrationConstants.SYNC_LOCK_ORCH_LOCK) {
			Assert.isTrue(candidate.isLocked(), "Unlocked candidate is intended to being reserved");

			final String jobIdStr = jobId.toString();
			final ZonedDateTime expiresAt = Utilities.utcNow().plusSeconds(RESERVATION_BUFFER + Math.min(duration, candidate.getExclusivityDuration()));

			final Optional<OrchestrationLock> optional = orchLockDbService.changeExpiresAtByOrchestrationJobIdAndServiceInstanceId(
					jobIdStr,
					candidate.getServiceInstance().instanceId(),
					expiresAt,
					false);

			// the temporary lock may expire
			if (optional.isEmpty()) {
				orchLockDbService.create(List.of(new OrchestrationLock(jobIdStr, candidate.getServiceInstance().instanceId(), consumerSystem, expiresAt, false)));
			}

			candidate.setExclusiveUntil(expiresAt);
		}
	}

	//-------------------------------------------------------------------------------------------------
	private void releaseTemporaryLockIfItWasLocked(final UUID jobId, final List<OrchestrationCandidate> candidates) {
		logger.debug("releaseTemporaryLockIfItWasLocked started...");

		if (Utilities.isEmpty(candidates)) {
			return;
		}

		synchronized (DynamicServiceOrchestrationConstants.SYNC_LOCK_ORCH_LOCK) {
			final String jobIdStr = jobId.toString();

			final List<String> lockedOnes = candidates
					.stream()
					.filter(c -> c.isLocked())
					.map(c -> c.getServiceInstance().instanceId())
					.collect(Collectors.toList());

			if (Utilities.isEmpty(lockedOnes)) {
				return;
			}

			final List<OrchestrationLock> lockRecords = orchLockDbService.getByServiceInstanceId(lockedOnes);

			final List<Long> toRelease = lockRecords
					.stream()
					.filter(lr -> !Utilities.isEmpty(lr.getOrchestrationJobId()) && lr.getOrchestrationJobId().equals(jobIdStr))
					.map(lr -> lr.getId())
					.collect(Collectors.toList());

			if (!Utilities.isEmpty(toRelease)) {
				orchLockDbService.deleteInBatch(toRelease);
			}
		}
	}

	//-------------------------------------------------------------------------------------------------
	private void obtainAuthorizationTokensIfRequired(final OrchestrationForm form, final List<OrchestrationCandidate> candidates) {
		logger.debug("obtainAuthorizationTokensIfRequired started...");

		final List<String> scopeList = Utilities.isEmpty(form.getOperations()) ? List.of("") : form.getOperations();
		final List<AuthorizationTokenGenerationMgmtRequestDTO> requestPayloadEntries = new ArrayList<>();
		for (final OrchestrationCandidate candidate : candidates) {
			Assert.isTrue(!candidate.isNonNative(), "There is a non-native candidte before generate-token attempt.");

			candidate.getMatchingInterfaces().forEach(interf -> {
				if (interf.policy().endsWith(Constants.AUTHORIZATION_TOKEN_VARIANT_SUFFIX)) {
					scopeList.forEach(scope -> {
						requestPayloadEntries.add(new AuthorizationTokenGenerationMgmtRequestDTO(
								interf.policy(),
								AuthorizationTargetType.SERVICE_DEF.name(),
								Defaults.DEFAULT_CLOUD,
								form.getTargetSystemName(),
								candidate.getServiceInstance().provider().name(),
								candidate.getServiceInstance().serviceDefinition().name(),
								scope,
								null,
								null));
					});
				}
			});
		}

		if (Utilities.isEmpty(requestPayloadEntries)) {
			return;
		}

		final AuthorizationTokenMgmtListResponseDTO response = ahHttpService.consumeService(
				Constants.SERVICE_DEF_AUTHORIZATION_TOKEN_MANAGEMENT,
				Constants.SERVICE_OP_AUTHORIZATION_GENERATE_TOKENS,
				Constants.SYS_NAME_CONSUMER_AUTHORIZATION,
				AuthorizationTokenMgmtListResponseDTO.class,
				new AuthorizationTokenGenerationMgmtListRequestDTO(requestPayloadEntries),
				new LinkedMultiValueMap<>(Map.of(Constants.UNBOUND, List.of(Boolean.TRUE.toString()))));

		for (final AuthorizationTokenResponseDTO tokenResult : response.entries()) {
			for (final OrchestrationCandidate candidate : candidates) {
				if (candidate.getServiceInstance().provider().name().equals(tokenResult.provider())) {
					candidate.addAuthorizationToken(
							tokenResult.variant(),
							Utilities.isEmpty(tokenResult.scope()) ? tokenResult.target() : tokenResult.scope(),
							new AuthorizationTokenGenerationResponseDTO(tokenResult.tokenType(), tokenResult.targetType(), tokenResult.token(), tokenResult.usageLimit(), tokenResult.expiresAt()));
					break;
				}
			}
		}
	}

	//-------------------------------------------------------------------------------------------------
	private void buildTranslationBridge(final String translationBridgeId, final String operation, final OrchestrationCandidate candidate) {
		logger.debug("buildTranslationBridge started...");
		Assert.isTrue(candidate.isNonNative(), "Candidate for translation bridge initiation is native!");

		final TranslationNegotiationMgmtRequestDTO negotiationRequest = new TranslationNegotiationMgmtRequestDTO(translationBridgeId, candidate.getServiceInstance().instanceId());
		final TranslationNegotiationResponseDTO negotiationResponse = ahHttpService.consumeService(
				Constants.SERVICE_DEF_TRANSLATION_BRIDGE_MANAGEMENT,
				Constants.SERVICE_OP_NEGOTIATION,
				Constants.SYS_NAME_TRANSLATIONMANAGER,
				TranslationNegotiationResponseDTO.class,
				negotiationRequest);
		logger.debug("Translation bridge has been created with id of '" + negotiationResponse.bridgeId() + "' and with target interface '" + candidate.getMatchingInterfaces().getFirst().templateName() + "'.");

		Assert.isTrue(translationBridgeId.equals(negotiationResponse.bridgeId()), "Translation bridge ID is different in negotiation response.");
		Assert.isTrue(Utilities.isEnumValue(negotiationResponse.bridgeInterface().policy(), ServiceInterfacePolicy.class), "Transation bridge interface policy should be a ServiceInterfacePolicy enum");

		candidate.getMatchingInterfaces().clear();
		candidate.addMatchingInterface(negotiationResponse.bridgeInterface());

		candidate.addAuthorizationToken(
				negotiationResponse.bridgeInterface().policy(),
				operation,
				new AuthorizationTokenGenerationResponseDTO(
						AuthorizationTokenType.fromServiceInterfacePolicy(ServiceInterfacePolicy.valueOf(negotiationResponse.bridgeInterface().policy())),
						AuthorizationTargetType.SERVICE_DEF,
						translationBridgeId,
						null,
						null)); // TODO would be good to have usageLimit or expiresAt if any.
	}

	//-------------------------------------------------------------------------------------------------
	private OrchestrationResponseDTO doInterCloudOrReturn(final UUID jobId, final OrchestrationForm form) {
		logger.debug("doInterCloudOrReturn started...");

		if (sysInfo.isIntercloudEnabled() && form.getFlag(OrchestrationFlag.ALLOW_INTERCLOUD)) {
			return interCloudOrch.doInterCloudServiceOrchestration(jobId, form);
		} else {
			orchJobDbService.setStatus(jobId, OrchestrationJobStatus.DONE, "No results were found");
			return new OrchestrationResponseDTO(List.of(), List.of());
		}
	}

	//-------------------------------------------------------------------------------------------------
	private OrchestrationResponseDTO convertToOrchestrationResponse(final List<OrchestrationCandidate> candidates, final Set<String> warnings) {
		logger.debug("convertToOrchestrationResponse started...");

		final List<OrchestrationResultDTO> results = candidates.stream()
				.map(c -> new OrchestrationResultDTO(
						c.getServiceInstance().instanceId(),
						Defaults.DEFAULT_CLOUD, // local cloud
						c.getServiceInstance().provider().name(),
						c.getServiceInstance().serviceDefinition().name(),
						c.getServiceInstance().version(),
						c.getServiceInstance().expiresAt(),
						Utilities.convertZonedDateTimeToUTCString(c.getExclusiveUntil()),
						c.getServiceInstance().metadata(),
						c.getMatchingInterfaces(),
						c.getAuthorizationTokens()))
				.collect(Collectors.toList());

		return new OrchestrationResponseDTO(results, new ArrayList<>(warnings));
	}
}