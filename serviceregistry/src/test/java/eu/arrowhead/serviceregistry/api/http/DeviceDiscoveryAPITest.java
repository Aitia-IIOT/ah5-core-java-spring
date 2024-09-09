package eu.arrowhead.serviceregistry.api.http;

import static org.junit.jupiter.api.Assertions.*;

import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.dto.AddressDTO;
import eu.arrowhead.dto.DeviceRequestDTO;
import eu.arrowhead.dto.DeviceResponseDTO;
import eu.arrowhead.dto.ErrorMessageDTO;
import eu.arrowhead.dto.enums.AddressType;
import eu.arrowhead.serviceregistry.ServiceRegistryConstants;
import eu.arrowhead.serviceregistry.jpa.entity.Device;
import eu.arrowhead.serviceregistry.jpa.entity.DeviceAddress;
import eu.arrowhead.serviceregistry.jpa.repository.DeviceAddressRepository;
import eu.arrowhead.serviceregistry.jpa.repository.DeviceRepository;
import eu.arrowhead.serviceregistry.jpa.service.DeviceDbService;
import jakarta.servlet.ServletContext;

import com.fasterxml.jackson.core.type.TypeReference;

@SpringBootTest
public class DeviceDiscoveryAPITest {
	
	//=================================================================================================
	// members
	
    private MockMvc mockMvc;
    
    //@MockBean
    //private DeviceRepository mockDeviceRepo;
    
    //@MockBean
    //private DeviceAddressRepository mockDeviceAddressRepo;
    
    @MockBean
    private DeviceDbService mockDbService;
    
    @Autowired
    private WebApplicationContext wac;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    //@Autowired
    //private static FilterChainProxy springSecurityFilterChain;
    
    private final String testDeviceName = "test-device-8";
    
    private final Map<String, Object> testMetadata = Map.of("good_quality", true);
    private final Map<String, Object> differentTestMetadata = Map.of("good_quality", false);
    
    private final AddressDTO testAddress1 = new AddressDTO("MAC", "22:22:22:22:22:22");
    private final AddressDTO testAddress2 = new AddressDTO("MAC", "2a:2a:2a:aa:aa:aa");
    private final AddressDTO differentTestAddress = new AddressDTO("MAC", "22:22:22:22:22:21");
    
    
    private final DeviceRequestDTO testDeviceDto = new DeviceRequestDTO(testDeviceName, testMetadata, List.of(testAddress1, testAddress2));
    private final DeviceRequestDTO sameTestDeviceDtoDifferentMetadataDto = new DeviceRequestDTO(testDeviceName, differentTestMetadata, List.of(testAddress1, testAddress2));
    private final DeviceRequestDTO sameTestDeviceDtoDifferentAddressDto = new DeviceRequestDTO(testDeviceName, testMetadata, List.of(testAddress1, differentTestAddress));
    private final DeviceRequestDTO sameTestDeviceDtoDifferentAddressOrderDto = new DeviceRequestDTO(testDeviceName, testMetadata, List.of(testAddress2, testAddress1));
    
    private final String REGISTER_DEVICE_METADATA_NOT_MATCHING_MESSAGE = "Device with name '" + testDeviceName + "' already exists, but provided metadata is not matching";
    private final String REGISTER_DEVICE_INTERFACES_NOT_MATCHING_MESSAGE = "Device with name " + testDeviceName + " already exists, but provided interfaces are not matching";
    
    @BeforeEach
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }
    
    //-------------------------------------------------------------------------------------------------
    // REGISTER:
    // * register new device (expectation: 201, DeviceResponseDTO)
    // * register device with existing name and same properties (expectation: 200, DeviceResponseDTO)
    // * register device with existing name and different metadata (expectation: 400, errorMessage: "Device with name '____' already exists, but provided metadata is not matching")
    // * register device with existing name and different addresses (expectation: 400, errorMessage: "Device with name '____' already exists, but provided interfaces are not matching")
    // * register device with existing name and same addresses but in a different order (expectation: 200, DeviceResponseDTO)
    
    //-------------------------------------------------------------------------------------------------
	@Test
	public void testDeviceDiscovery_PostNewDevice_ReturnCreated() throws Exception {
		
		Mockito.when(mockDbService.create(Mockito.any(DeviceRequestDTO.class))).thenAnswer(invocation -> createDeviceEntry(invocation.getArgument(0)));
		
		final MvcResult response = mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(testDeviceDto)))
				.andExpect(status().is(201))
				.andReturn();
		
		final DeviceResponseDTO responseBody = objectMapper.readValue(response.getResponse().getContentAsString(), DeviceResponseDTO.class);
		assertEquals(responseBody.name(),testDeviceDto.name());
	}
	
	//-------------------------------------------------------------------------------------------------
	@Test
	public void testDeviceDiscovery_PostExistingDevice_ReturnOk() throws Exception {
		
		final Device testDevice = new Device(testDeviceName, Utilities.toJson(testMetadata));
		
		Mockito.when(mockDbService.getByName(Mockito.any(String.class))).thenAnswer(invocation -> Optional.of(Map.entry(testDevice, List.of(
				new DeviceAddress(testDevice, AddressType.valueOf(testAddress1.type()), testAddress1.address()), 
				new DeviceAddress(testDevice, AddressType.valueOf(testAddress2.type()), testAddress2.address())))));
		
		final MvcResult response = mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(testDeviceDto)))
				.andExpect(status().isOk())
				.andReturn();
		
		final DeviceResponseDTO responseBody = objectMapper.readValue(response.getResponse().getContentAsString(), DeviceResponseDTO.class);
		assertEquals(responseBody.name(),testDeviceDto.name());
	}
	
	//-------------------------------------------------------------------------------------------------
	@Test
	public void testDeviceDiscovery_PostExistingDeviceNameDifferentMetadata_ReturnBadRequest() throws Exception {
		
		final Device testDevice = new Device(testDeviceName, Utilities.toJson(testMetadata));
		
		Mockito.when(mockDbService.getByName(Mockito.any(String.class))).thenAnswer(invocation -> Optional.of(Map.entry(testDevice, List.of(
				new DeviceAddress(testDevice, AddressType.valueOf(testAddress1.type()), testAddress1.address()), 
				new DeviceAddress(testDevice, AddressType.valueOf(testAddress2.type()), testAddress2.address())))));
		
		mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(sameTestDeviceDtoDifferentMetadataDto)))
				.andExpect(status().isBadRequest())
				.andReturn();
		
	}
	
	//-------------------------------------------------------------------------------------------------
	@Test
	public void testDeviceDiscovery_PostExistingDeviceNameDifferentAddressList_ReturnBadRequest() throws Exception {
		
		final Device testDevice = new Device(testDeviceName, Utilities.toJson(testMetadata));
		
		Mockito.when(mockDbService.getByName(Mockito.any(String.class))).thenAnswer(invocation -> Optional.of(Map.entry(testDevice, List.of(
				new DeviceAddress(testDevice, AddressType.valueOf(testAddress1.type()), testAddress1.address()), 
				new DeviceAddress(testDevice, AddressType.valueOf(testAddress2.type()), testAddress2.address())))));
		
		final MvcResult response = mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(sameTestDeviceDtoDifferentMetadataDto)))
				.andExpect(status().isBadRequest())
				.andReturn();
		
		final ErrorMessageDTO error = objectMapper.readValue(response.getResponse().getContentAsString(), ErrorMessageDTO.class);
		
		assertEquals(REGISTER_DEVICE_METADATA_NOT_MATCHING_MESSAGE, error.errorMessage());
	}
	
	// LOOKUP
	
	// REVOKE
	
	//=================================================================================================
	// assistant methods
	
	//TODO: create devices for mocking
	
	//-------------------------------------------------------------------------------------------------
	private Entry<Device, List<DeviceAddress>> createDeviceEntry(final DeviceRequestDTO request) {
		final Device device = new Device(request.name(), Utilities.toJson(request.metadata()));
		final List<DeviceAddress> deviceAddressList = request.addresses()
				.stream()
				.map(a -> new DeviceAddress(device, AddressType.valueOf(a.type()), a.address()))
				.collect(Collectors.toList());
		return new AbstractMap.SimpleEntry<Device, List<DeviceAddress>>(device, deviceAddressList);
	}

}
