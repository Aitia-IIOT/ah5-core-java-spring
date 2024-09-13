package eu.arrowhead.serviceregistry.service.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.type.TypeReference;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.dto.AddressDTO;
import eu.arrowhead.dto.DeviceListResponseDTO;
import eu.arrowhead.dto.DeviceResponseDTO;
import eu.arrowhead.dto.ServiceDefinitionListResponseDTO;
import eu.arrowhead.dto.ServiceDefinitionResponseDTO;
import eu.arrowhead.dto.SystemListResponseDTO;
import eu.arrowhead.dto.SystemResponseDTO;
import eu.arrowhead.serviceregistry.jpa.entity.Device;
import eu.arrowhead.serviceregistry.jpa.entity.DeviceAddress;
import eu.arrowhead.serviceregistry.jpa.entity.ServiceDefinition;
import eu.arrowhead.serviceregistry.jpa.entity.SystemAddress;
import eu.arrowhead.serviceregistry.jpa.entity.System;

@Service
public class DTOConverter {

	//=================================================================================================
	// members

	private final Logger logger = LogManager.getLogger(this.getClass());

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	public DeviceListResponseDTO convertDeviceAndDeviceAddressEntriesToDTO(final Iterable<Entry<Device, List<DeviceAddress>>> entries, final long count) {
		logger.debug("convertDeviceAndDeviceAddressEntriesToDTO started...");
		Assert.notNull(entries, "entry list is null");

		final List<DeviceResponseDTO> dtos = new ArrayList<>();
		entries.forEach(entry -> dtos.add(convertDeviceEntityToDeviceResponseDTO(entry.getKey(), entry.getValue())));

		return new DeviceListResponseDTO(dtos, count);
	}

	//-------------------------------------------------------------------------------------------------
	public DeviceResponseDTO convertDeviceEntityToDeviceResponseDTO(final Device deviceEntitiy, final List<DeviceAddress> addressEntities) {
		logger.debug("convertDeviceAddressEntityListToDTO started...");
		Assert.notNull(deviceEntitiy, "device entity is null");

		return new DeviceResponseDTO(
				deviceEntitiy.getName(),
				Utilities.fromJson(deviceEntitiy.getMetadata(), new TypeReference<Map<String, Object>>() { }),
				Utilities.isEmpty(addressEntities) ? null
						: addressEntities.stream()
								.map(address -> new AddressDTO(address.getAddressType().name(), address.getAddress()))
								.collect(Collectors.toList()),
				Utilities.convertZonedDateTimeToUTCString(deviceEntitiy.getCreatedAt()),
				Utilities.convertZonedDateTimeToUTCString(deviceEntitiy.getUpdatedAt()));
	}

	//-------------------------------------------------------------------------------------------------
	public ServiceDefinitionListResponseDTO convertServiceDefinitionEntityListToDTO(final List<ServiceDefinition> entities) {
		logger.debug("convertServiceDefinitionEntityListToDTO started...");
		Assert.isTrue(!Utilities.isEmpty(entities), "entity list is empty");

		final List<ServiceDefinitionResponseDTO> converted = entities.stream()
				.map(e -> convertServiceDefinitionEntityToDTO(e))
				.collect(Collectors.toList());
		return new ServiceDefinitionListResponseDTO(converted, converted.size());
	}

	//-------------------------------------------------------------------------------------------------
	public ServiceDefinitionResponseDTO convertServiceDefinitionEntityToDTO(final ServiceDefinition entity) {
		logger.debug("convertServiceDefinitionEntityToDTO started...");
		Assert.notNull(entity, "entity is null");
		Assert.isTrue(!Utilities.isEmpty(entity.getName()), "name is empty");

		return new ServiceDefinitionResponseDTO(entity.getName(), Utilities.convertZonedDateTimeToUTCString(entity.getCreatedAt()), Utilities.convertZonedDateTimeToUTCString(entity.getUpdatedAt()));
	}

	//-------------------------------------------------------------------------------------------------
	public SystemListResponseDTO convertSystemTriplesToDTO(final List<Triple<System, List<SystemAddress>, Entry<Device, List<DeviceAddress>>>> entities) {
		logger.debug("convertSystemTriplesToDTO started...");
		Assert.isTrue(!Utilities.isEmpty(entities), "entity list is empty");

		final List<SystemResponseDTO> result = new ArrayList<>();

		for (final Triple<System, List<SystemAddress>, Entry<Device, List<DeviceAddress>>> entity : entities) {
			result.add(convertSystemTripleToDTO(entity));
		}

		return new SystemListResponseDTO(result, result.size());
	}

	//-------------------------------------------------------------------------------------------------
	public SystemResponseDTO convertSystemTripleToDTO(final Triple<System, List<SystemAddress>, Entry<Device, List<DeviceAddress>>> entity) {
		logger.debug("convertSystemTripleToDTO started...");
		Assert.notNull(entity.getLeft(), "the System in the triple is null");
		Assert.isTrue(!Utilities.isEmpty(entity.getMiddle()), "the address list in the triple is null");

		final System system = entity.getLeft();
		final List<SystemAddress> systemAddressList = entity.getMiddle();
		final Device device = entity.getRight().getKey();
		final List<DeviceAddress> deviceAddresses = entity.getRight().getValue();

		return new SystemResponseDTO(
				system.getName(),
				Utilities.fromJson(system.getMetadata(), new TypeReference<Map<String, Object>>() { }),
				system.getVersion(),
				systemAddressList
					.stream()
					.map(a -> new AddressDTO(a.getAddressType().toString(), a.getAddress()))
					.collect(Collectors.toList()),
				device == null ? null : new DeviceResponseDTO(
						device.getName(),
						Utilities.fromJson(device.getMetadata(), new TypeReference<Map<String, Object>>() { }),
						deviceAddresses.stream().map(a -> new AddressDTO(a.getAddressType().toString(), a.getAddress())).collect(Collectors.toList()),
						Utilities.convertZonedDateTimeToUTCString(device.getCreatedAt()),
						Utilities.convertZonedDateTimeToUTCString(device.getUpdatedAt())),
				Utilities.convertZonedDateTimeToUTCString(system.getCreatedAt()),
				Utilities.convertZonedDateTimeToUTCString(system.getUpdatedAt()));
	}
	
	//=================================================================================================
	// assistant methods
}
