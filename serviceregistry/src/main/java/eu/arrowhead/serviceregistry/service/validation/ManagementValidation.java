package eu.arrowhead.serviceregistry.service.validation;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.common.service.validation.PageValidator;
import eu.arrowhead.dto.AddressDTO;
import eu.arrowhead.dto.ServiceDefinitionListRequestDTO;
import eu.arrowhead.dto.SystemListRequestDTO;
import eu.arrowhead.dto.SystemQueryRequestDTO;
import eu.arrowhead.dto.SystemRequestDTO;
import eu.arrowhead.dto.enums.AddressType;
import eu.arrowhead.serviceregistry.service.validation.address.AddressTypeValidator;
import eu.arrowhead.serviceregistry.jpa.entity.System;

@Service
public class ManagementValidation {

	//=================================================================================================
	// members
	
	@Autowired
	private AddressTypeValidator addressTypeValidator;
	
	@Autowired
	private PageValidator pageValidator;

	private final Logger logger = LogManager.getLogger(this.getClass());

	//=================================================================================================
	// methods
	
	//-------------------------------------------------------------------------------------------------
	public void validateNormalizedAddress(final AddressDTO dto, final String origin) {
		logger.debug("validateNormalizedAddress started");
		Assert.isTrue(Utilities.isEnumValue(dto.type(), AddressType.class), "address type is invalid");

		try {
			addressTypeValidator.validateNormalizedAddress(AddressType.valueOf(dto.type()), dto.address());
		} catch (final InvalidParameterException ex) {
			throw new InvalidParameterException(ex.getMessage(), origin);
		}
	}

	//-------------------------------------------------------------------------------------------------
	public void validateCreateServiceDefinition(final ServiceDefinitionListRequestDTO dto, final String origin) {
		logger.debug("validateCreateServiceDefinition started");

		if (dto == null) {
			throw new InvalidParameterException("Request payload is missing", origin);
		}

		if (Utilities.isEmpty(dto.serviceDefinitionNames())) {
			throw new InvalidParameterException("Request payload is empty", origin);
		}

		for (final String definitionName : dto.serviceDefinitionNames()) {
			if (Utilities.isEmpty(definitionName)) {
				throw new InvalidParameterException("Service definition name is missing", origin);
			}

			// verify no duplicates in list

			// TODO: max 63 chars and naming convention!
		}
	}
	
	//-------------------------------------------------------------------------------------------------
	public void validateCreateSystem(final SystemListRequestDTO dto, final String origin) {
		logger.debug("validateCreateSystem started");
		
		if (dto == null) {
			throw new InvalidParameterException("Request payload is missing", origin);
		}

		if (Utilities.isEmpty(dto.systems())) {
			throw new InvalidParameterException("Request payload is empty", origin);
		}
		
		final Set<String> names = new HashSet<>();
		
		for (final SystemRequestDTO system : dto.systems()) {
			
			if (system == null) {
				throw new InvalidParameterException("System list contains null element", origin);
			}
			
			if (Utilities.isEmpty(system.name())) {
				throw new InvalidParameterException("System name is empty", origin);
			}

			if (names.contains(system.name())) {
				throw new InvalidParameterException("Duplicate system name: " + system.name(), origin);
			}
			names.add(system.name());

			if (!Utilities.isEmpty(system.addresses())) {
				for (final AddressDTO address : system.addresses()) {
					
					if (address == null) {
						throw new InvalidParameterException("Address list contains null element", origin);
					}

					if (Utilities.isEmpty(address.type())) {
						throw new InvalidParameterException("Address type is missing", origin);
					}

					if (!Utilities.isEnumValue(address.type(), AddressType.class)) {
						throw new InvalidParameterException("Invalid address type: " + address.type(), origin);
					}

					if (Utilities.isEmpty(address.address())) {
						throw new InvalidParameterException("Address value is missing", origin);
					}
				}
			}
		}
	}
	
	//-------------------------------------------------------------------------------------------------
	public void validateQuerySystems(final SystemQueryRequestDTO dto, final String origin) {
		logger.debug("validateQuerySystems started");
		
		if (dto == null) {
			throw new InvalidParameterException("Request payload is missing", origin);
		}
		
		pageValidator.validatePageParameter(dto.pagination(), System.SORTABLE_FIELDS_BY, origin);
		
		if (!Utilities.isEmpty(dto.systemNames()) && Utilities.containsNullOrEmpty(dto.systemNames())) {
			throw new InvalidParameterException("System name list contains null or empty element", origin);
		}
		
		if (!Utilities.isEmpty(dto.addresses()) && Utilities.containsNullOrEmpty(dto.addresses())) {
			throw new InvalidParameterException("Address list contains null or empty element", origin);
		}
		
		if (!Utilities.isEmpty(dto.addressType()) && !Utilities.isEnumValue(dto.addressType(), AddressType.class)) {
			throw new InvalidParameterException("Invalid address type: " + dto.addressType(), origin);
		}
		
		if (!Utilities.isEmpty(dto.metadataRequirementList()) && Utilities.containsNull(dto.metadataRequirementList())) {
			throw new InvalidParameterException("Metadata requirement list contains null element", origin);
		}
		
		if (!Utilities.isEmpty(dto.versions()) && Utilities.containsNullOrEmpty(dto.versions())) {
			throw new InvalidParameterException("Version list contains null or empty element", origin);
		}
		
		if (!Utilities.isEmpty(dto.deviceNames()) && Utilities.containsNullOrEmpty(dto.deviceNames())) {
			throw new InvalidParameterException("Device name list contains null or empty element", origin);
		}
	}

}
