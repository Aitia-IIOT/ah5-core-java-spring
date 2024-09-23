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

import eu.arrowhead.common.exception.InternalServerError;
import eu.arrowhead.common.exception.InvalidParameterException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.mockito.Mockito;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.dto.DeviceListResponseDTO;
import eu.arrowhead.dto.DeviceLookupRequestDTO;
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
    private final String LOOKUP_DEVICE_INVALID_DEVICE_NAME_LIST__MESSAGE = "Device name list contains null or empty element";
    private final String INTERNAL_SERVER_ERROR_MESSAGE = "Database operation error";
    
    @BeforeEach
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }
    
    //MINDEGYIKRE:
    // * forbidden
    // * unauthorized

    
    //-------------------------------------------------------------------------------------------------
    @Test
	public void RegisterNewDevice_ReturnCreated() throws Exception {
		
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
	public void RegisterExistingDevice_ReturnOk() throws Exception {
		
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
	public void RegisterExistingDevice_DifferentMetadata_ReturnBadRequest() throws Exception {
		
		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH;
		
		Mockito.when(mockDeviceDiscoveryService.registerDevice(testDeviceDto, origin))
			.thenThrow(new InvalidParameterException(REGISTER_DEVICE_METADATA_NOT_MATCHING_MESSAGE, origin));
		
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
	public void RegisterExistingDevice_DifferentAddresses_ReturnBadRequest() throws Exception {
		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH;
		
		Mockito.when(mockDeviceDiscoveryService.registerDevice(testDeviceDto, origin))
			.thenThrow(new InvalidParameterException(REGISTER_DEVICE_INTERFACES_NOT_MATCHING_MESSAGE, origin));
		
		final MvcResult response = mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(testDeviceDto)))
				.andExpect(status().isBadRequest())
				.andReturn();
		
		final ErrorMessageDTO error = objectMapper.readValue(response.getResponse().getContentAsString(), ErrorMessageDTO.class);
		
		assertEquals(REGISTER_DEVICE_INTERFACES_NOT_MATCHING_MESSAGE, error.errorMessage());
	}
	
	//-------------------------------------------------------------------------------------------------
	@Test
	public void RegisterDevice_DbOperationError_ReturnInternalServerError() throws Exception {
		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH;
	
		Mockito.when(mockDeviceDiscoveryService.registerDevice(testDeviceDto, origin))
			.thenThrow(new InternalServerError(INTERNAL_SERVER_ERROR_MESSAGE, origin));
	
		final MvcResult response = mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(testDeviceDto)))
				.andExpect(status().isInternalServerError())
				.andReturn();
		
		final ErrorMessageDTO error = objectMapper.readValue(response.getResponse().getContentAsString(), ErrorMessageDTO.class);
		
		assertEquals(INTERNAL_SERVER_ERROR_MESSAGE, error.errorMessage());
	}
	
	// LOOKUP
	
	// * 200 ok
	// * 400 invalid parameter exception
	// * 500 internal server error
	
	//-------------------------------------------------------------------------------------------------
	@Test
	public void LookupDevice_ReturnOk() throws Exception {
		
		DeviceLookupRequestDTO dto = new DeviceLookupRequestDTO(List.of(), List.of(), null, List.of());
		
		Mockito.when(mockDeviceDiscoveryService.lookupDevice(Mockito.any(DeviceLookupRequestDTO.class), Mockito.anyString()))
			.thenAnswer(invocation -> new DeviceListResponseDTO(List.of(), 0));

		
		final MvcResult response = mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_LOOKUP_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(Utilities.toJson(dto)))
				.andExpect(status().isOk())
				.andReturn();
		
		final DeviceListResponseDTO responseBody = objectMapper.readValue(response.getResponse().getContentAsString(), DeviceListResponseDTO.class);
		assertEquals(DeviceListResponseDTO.class, responseBody.getClass());
	}
	
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
