package eu.arrowhead.serviceregistry.api.http;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eu.arrowhead.common.exception.InvalidParameterException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.mockito.Mockito;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.dto.DeviceRequestDTO;
import eu.arrowhead.dto.DeviceResponseDTO;
import eu.arrowhead.dto.ErrorMessageDTO;
import eu.arrowhead.serviceregistry.ServiceRegistryConstants;
import eu.arrowhead.serviceregistry.jpa.entity.Device;
import eu.arrowhead.serviceregistry.service.DeviceDiscoveryService;

import com.fasterxml.jackson.core.type.TypeReference;

@SpringBootTest
public class DeviceDiscoveryAPITest {
	
	//=================================================================================================
	// members
	
    private MockMvc mockMvc;

    @MockBean
    private DeviceDiscoveryService mockDeviceDiscoveryService;
    
    @Autowired
    private WebApplicationContext wac;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private final String testDeviceName = "test-device-8";
    private final DeviceRequestDTO testDeviceDto = new DeviceRequestDTO(testDeviceName, Map.of(), List.of());
    
    private final String REGISTER_DEVICE_METADATA_NOT_MATCHING_MESSAGE = "Device with name '" + testDeviceName + "' already exists, but provided metadata is not matching";
    private final String REGISTER_DEVICE_INTERFACES_NOT_MATCHING_MESSAGE = "Device with name '" + testDeviceName + "' already exists, but provided interfaces are not matching";
    
    @BeforeEach
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }
    
    //MINDEGYIKRE:
    // * forbidden
    // * unauthorized
    // * internal server error

    
    //-------------------------------------------------------------------------------------------------
    @Test
	public void PostNewDevice_ReturnCreated() throws Exception {
		
		Mockito.when(mockDeviceDiscoveryService.registerDevice(Mockito.any(DeviceRequestDTO.class), Mockito.anyString()))
			.thenAnswer(invocation -> Map.entry(createDeviceResponseDTO(invocation.getArgument(0)), true));
		
		final MvcResult response = mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(testDeviceDto)))
				.andExpect(status().isCreated())
				.andReturn();
		
		final DeviceResponseDTO responseBody = objectMapper.readValue(response.getResponse().getContentAsString(), DeviceResponseDTO.class);
		assertEquals(responseBody.name(),testDeviceDto.name());
	}
	
	//-------------------------------------------------------------------------------------------------
	@Test
	public void PostExistingDevice_ReturnOk() throws Exception {
		
		Mockito.when(mockDeviceDiscoveryService.registerDevice(Mockito.any(DeviceRequestDTO.class), Mockito.anyString()))
			.thenAnswer(invocation -> Map.entry(createDeviceResponseDTO(invocation.getArgument(0)), false));
		
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
	public void PostExistingDevice_DifferentMetadata_ReturnBadRequest() throws Exception {
		
		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH;
		
		Mockito.when(mockDeviceDiscoveryService.registerDevice(testDeviceDto, origin))
			.thenThrow(new InvalidParameterException("Device with name '" + testDeviceDto.name() + "' already exists, but provided metadata is not matching", origin));
		
		final MvcResult response = mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(testDeviceDto)))
				.andExpect(status().isBadRequest())
				.andReturn();
		
		final ErrorMessageDTO error = objectMapper.readValue(response.getResponse().getContentAsString(), ErrorMessageDTO.class);
		assertEquals(REGISTER_DEVICE_METADATA_NOT_MATCHING_MESSAGE, error.errorMessage());
	}
	
	//-------------------------------------------------------------------------------------------------
	@Test
	public void PostExistingDevice_DifferentAddresses_ReturnBadRequest() throws Exception {
		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH;
		
		Mockito.when(mockDeviceDiscoveryService.registerDevice(testDeviceDto, origin))
			.thenThrow(new InvalidParameterException("Device with name '" + testDeviceDto.name() + "' already exists, but provided interfaces are not matching", origin));
		
		final MvcResult response = mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(testDeviceDto)))
				.andExpect(status().isBadRequest())
				.andReturn();
		
		final ErrorMessageDTO error = objectMapper.readValue(response.getResponse().getContentAsString(), ErrorMessageDTO.class);
		
		assertEquals(REGISTER_DEVICE_INTERFACES_NOT_MATCHING_MESSAGE, error.errorMessage());
	}
	
	// LOOKUP
	
	// REVOKE
	
	//=================================================================================================
	// assistant methods
	
	//TODO: create devices for mocking
	//-------------------------------------------------------------------------------------------------
	private DeviceResponseDTO createDeviceResponseDTO(final DeviceRequestDTO request) {
		final Device device = new Device(request.name(), Utilities.toJson(request.metadata()));
		return new DeviceResponseDTO(device.getName(), Utilities.fromJson(device.getMetadata(), new TypeReference<Map<String, Object>>() {}), request.addresses(), null, null);
	}

}
