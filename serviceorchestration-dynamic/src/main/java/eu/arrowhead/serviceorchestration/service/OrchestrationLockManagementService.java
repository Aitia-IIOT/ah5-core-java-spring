package eu.arrowhead.serviceorchestration.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.InternalServerError;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.common.service.PageService;
import eu.arrowhead.dto.OrchestrationLockListRequestDTO;
import eu.arrowhead.dto.OrchestrationLockListResponseDTO;
import eu.arrowhead.dto.OrchestrationLockQueryRequestDTO;
import eu.arrowhead.serviceorchestration.jpa.entity.OrchestrationLock;
import eu.arrowhead.serviceorchestration.jpa.service.OrchestrationLockDbService;
import eu.arrowhead.serviceorchestration.service.dto.DTOConverter;
import eu.arrowhead.serviceorchestration.service.model.OrchestrationLockFilter;
import eu.arrowhead.serviceorchestration.service.validation.OrchestrationLockManagementValidation;

@Service
public class OrchestrationLockManagementService {

	//=================================================================================================
	// members

	@Autowired
	private OrchestrationLockManagementValidation validator;

	@Autowired
	private OrchestrationLockDbService lockDbService;

	@Autowired
	private PageService pageService;

	@Autowired
	private DTOConverter dtoConverter;

	private static final Object LOCK = new Object();

	private final Logger logger = LogManager.getLogger(this.getClass());

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	public OrchestrationLockListResponseDTO create(final OrchestrationLockListRequestDTO dto, final String origin) {
		logger.debug("create started..");

		final OrchestrationLockListRequestDTO normalized = validator.validateAndNormalizeCreateService(dto, origin);
		final ZonedDateTime now = Utilities.utcNow();
		List<OrchestrationLock> saved = null;

		synchronized (LOCK) {
			try {
				final List<OrchestrationLock> existingLocks = lockDbService.getByServiceInstanceId(normalized.locks().stream().map(l -> l.serviceInstanceId()).toList());
				final List<Long> expiredLockIds = new ArrayList<>();
				final List<String> alreadyLockedServices = new ArrayList<>();
				for (final OrchestrationLock existingLock : existingLocks) {
					if (!Utilities.isEmpty(existingLock.getOrchestrationJobId())
							&& existingLock.getExpiresAt() != null
							&& existingLock.getExpiresAt().isBefore(now)) {
						expiredLockIds.add(existingLock.getId());
					} else {
						alreadyLockedServices.add(existingLock.getServiceInstanceId());
					}
				}

				if (!Utilities.isEmpty(expiredLockIds)) {
					lockDbService.deleteInBatch(expiredLockIds);
				}

				if (!Utilities.isEmpty(alreadyLockedServices)) {
					throw new InvalidParameterException("Already locked: " + alreadyLockedServices.stream().collect(Collectors.joining(", ")), origin);
				}

				final List<OrchestrationLock> candidates = normalized.locks().stream().map(lock -> new OrchestrationLock(lock.serviceInstanceId(), lock.owner(), Utilities.parseUTCStringToZonedDateTime(lock.expiresAt()))).toList();
				saved = lockDbService.create(candidates);

			} catch (final InternalServerError ex) {
				throw new InternalServerError(ex.getMessage(), origin);
			}
		}

		return dtoConverter.converOrchestartionLockListToDTO(saved, saved.size());
	}

	//-------------------------------------------------------------------------------------------------
	public OrchestrationLockListResponseDTO query(final OrchestrationLockQueryRequestDTO dto, final String origin) {
		logger.debug("query started...");

		final OrchestrationLockQueryRequestDTO normalized = validator.validateAndNormalizeQueryService(dto, origin);

		final PageRequest pageRequest = pageService.getPageRequest(normalized.pagination(), Direction.DESC, OrchestrationLock.SORTABLE_FIELDS_BY, OrchestrationLock.DEFAULT_SORT_FIELD, origin);
		final OrchestrationLockFilter filter = new OrchestrationLockFilter(normalized);

		try {
			final Page<OrchestrationLock> results = lockDbService.query(filter, pageRequest);
			return dtoConverter.converOrchestartionLockListToDTO(results.getContent(), results.getTotalElements());

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}

	//-------------------------------------------------------------------------------------------------
	public void remove(final String owner, final List<String> serviceInstanceIds, final String origin) {
		logger.debug("remove started...");

		final Pair<String, List<String>> normalized = validator.validateAndNormalizeRemoveService(owner, serviceInstanceIds, origin);

		try {
			synchronized (LOCK) {
				final List<Long> removeIds = new ArrayList<>();
				final List<OrchestrationLock> records = lockDbService.getByServiceInstanceId(normalized.getRight());
				for (final OrchestrationLock record : records) {
					if (Utilities.isEmpty(record.getOrchestrationJobId())
							&& record.getOwner().equals(normalized.getLeft())) {
						removeIds.add(record.getId());
					}
				}

				if (!Utilities.isEmpty(removeIds)) {
					lockDbService.deleteInBatch(removeIds);
				}
			}

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}

	}
}
