package eu.arrowhead.serviceregistry.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.InternalServerError;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.dto.DeviceResponseDTO;
import eu.arrowhead.dto.SystemListResponseDTO;
import eu.arrowhead.dto.SystemLookupRequestDTO;
import eu.arrowhead.dto.SystemQueryRequestDTO;
import eu.arrowhead.dto.SystemRequestDTO;
import eu.arrowhead.dto.SystemResponseDTO;
import eu.arrowhead.serviceregistry.jpa.service.SystemDbService;
import eu.arrowhead.serviceregistry.service.matching.AddressMatching;
import eu.arrowhead.serviceregistry.service.validation.SystemDiscoveryValidation;
import eu.arrowhead.serviceregistry.jpa.entity.System;

@Service
public class SystemDiscoveryService {
	
	//=================================================================================================
	// members
	
	@Autowired
	private SystemDiscoveryValidation validator;
	
	@Autowired
	private AddressMatching addressMatcher;
	
	@Autowired
	private SystemDbService dbService;
	
    @Value("${service.discovery.verbose}")
    private boolean verboseEnabled;
	
	private final Logger logger = LogManager.getLogger(this.getClass());
	
	//=================================================================================================
	// methods
	
	//-------------------------------------------------------------------------------------------------
	public Entry<SystemResponseDTO, Boolean> registerSystem(final SystemRequestDTO dto, final String origin) {
	
		logger.debug("registerSystem started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");
		
		final SystemRequestDTO normalized = validator.validateAndNormalizeRegisterSystem(dto, origin);

		try {
			final Optional<SystemRequestDTO> optional = dbService.getByName(normalized.name());

			// Existing system
			if (optional.isPresent()) {
				final SystemRequestDTO existingSystemAsDTO = optional.get();
				
				// We should check if every property is the same
				checkSameSystemAttributes(existingSystemAsDTO, dto);

				//convert to response and return
				final System existing = dbService.getSystemByName(dto.name()).get();
				final SystemResponseDTO existingSystemAsResponseDTO = dbService.createSystemResponseDTO(existing);
				return Map.entry(existingSystemAsResponseDTO, false);
			}

			// New system
			final SystemResponseDTO response = dbService.createBulk(List.of(dto)).get(0);
			return Map.entry(response, true);
			
		} catch (final InvalidParameterException ex) {
			throw new InvalidParameterException(ex.getMessage(), origin);

		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}
	
	//-------------------------------------------------------------------------------------------------
	public SystemListResponseDTO lookupSystem(final SystemLookupRequestDTO dto, final boolean verbose, final String origin) {
		logger.debug("lookupSystem started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");
		
		final SystemLookupRequestDTO normalized = validator.validateAndNormalizeLookupSystem(dto, origin);
		
		try {
			List<SystemResponseDTO> result = dbService.getPageByFilters(
					new SystemQueryRequestDTO(
							null,
							normalized.systemNames(),
							normalized.addresses(),
							normalized.addressType(),
							normalized.metadataRequirementList(),
							normalized.versions(),
							normalized.deviceNames()), 
					origin);
			
			//we do not provide device information (except for the name), if the verbose mode is not enabled, or the user set it false in the query param
			if (!verbose || !verboseEnabled) {
				final List<SystemResponseDTO> resultTerse = new ArrayList<>();
				
				for (final SystemResponseDTO systemResponseDTO : result) {
					
					final DeviceResponseDTO device = new DeviceResponseDTO(systemResponseDTO.device().name(), null, null, null, null);
					
					resultTerse.add(new SystemResponseDTO(
							systemResponseDTO.name(),
							systemResponseDTO.metadata(),
							systemResponseDTO.version(),
							systemResponseDTO.addresses(),
							device,
							systemResponseDTO.createdAt(),
							systemResponseDTO.updatedAt()
							));
				}
				
				result = resultTerse;
			}
			
			return new SystemListResponseDTO(result, result.size());
		} catch (final InternalServerError ex ) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}
	
	//-------------------------------------------------------------------------------------------------
	public boolean revokeSystem(final String name, final String origin) {
		logger.debug("revokeSystem started");
		Assert.isTrue(!Utilities.isEmpty(origin), "origin is empty");

		validator.validateAndNormalizeRevokeSystem(name, origin);

		try {
			return dbService.deleteByName(name.trim());
		} catch (final InternalServerError ex) {
			throw new InternalServerError(ex.getMessage(), origin);
		}
	}
	
	//=================================================================================================
	// assistant methods
	
	//-------------------------------------------------------------------------------------------------
	// throws exception, if the two systems doesn't have the same attributes
	private void checkSameSystemAttributes(final SystemRequestDTO system1, final SystemRequestDTO system2) {
		logger.debug("checkSameSystemAttributes started");
		
		Assert.isTrue(system1.name().equals(system2.name()), "The systems are not identical!");
		
		//metadata
		if (!system1.metadata().equals(system2.metadata())) {
			throw new InvalidParameterException("System with name: " + system1.name() + " already exists, but provided metadata is not matching");
		}
		
		//version
		if (!system1.version().equals(system2.version())) {
			throw new InvalidParameterException("System with name: " + system1.name() + " already exists, but provided version is not matching");
		}
		
		//addresses
		if (!addressMatcher.isAddressListMatching(system1.addresses(), system2.addresses())) {
			throw new InvalidParameterException("System with name: " + system1.name() + " already exists, but provided address list is not matching");
		}
		
		//device name
		if (!system1.deviceName().equals(system2.deviceName())) {
			throw new InvalidParameterException("System with name: " + system1.name() + " already exists, but provided device name is not matching");
		}
	}
}
