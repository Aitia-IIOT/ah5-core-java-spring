package eu.arrowhead.serviceregistry.api.http;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.dto.AddressDTO;
import eu.arrowhead.dto.DeviceRequestDTO;
import eu.arrowhead.serviceregistry.ServiceRegistryConstants;
import eu.arrowhead.serviceregistry.jpa.entity.Device;

//@WebMvcTest(DeviceDiscoveryAPI.class)
@SpringBootTest
@AutoConfigureMockMvc
public class DeviceDiscoveryAPITest {
	
	//=================================================================================================
	// members
	//todo: kiszervezni konstansokba a stringeket
	
    @Autowired
    private MockMvc mockMvc;
    
    private final String testDeviceName = "test-device";
    
    private final Map<String, Object> testMetadata = Map.of("good_quality", true);
    private final Map<String, Object> differentTestMetadata = Map.of("good_quality", false);
    
    private final AddressDTO testAddress1 = new AddressDTO("MAC", "22:22:22:22:22:22");
    private final AddressDTO testAddress2 = new AddressDTO("MAC", "33:33:33:33:33:33");
    private final AddressDTO differentTestAddress = new AddressDTO("MAC", "22:22:22:22:22:21");
    
    private final DeviceRequestDTO testDeviceDto = new DeviceRequestDTO(testDeviceName, testMetadata, List.of(testAddress1, testAddress2));
    private final DeviceRequestDTO sameTestDeviceDtoDifferentMetadata = new DeviceRequestDTO(testDeviceName, differentTestMetadata, List.of(testAddress1, testAddress2));
    private final DeviceRequestDTO sameTestDeviceDtoDifferentAddress = new DeviceRequestDTO(testDeviceName, testMetadata, List.of(testAddress1, differentTestAddress));
    private final DeviceRequestDTO sameTestDeviceDtoDifferentAddressOrder = new DeviceRequestDTO(testDeviceName, testMetadata, List.of(testAddress2, testAddress1));
    
    private final String REGISTER_DEVICE_METADATA_NOT_MATCHING_MESSAGE = "Device with name " + testDeviceName + " already exists, but provided metadata is not matching";
    private final String REGISTER_DEVICE_INTERFACES_NOT_MATCHING_MESSAGE = "Device with name " + testDeviceName + " already exists, but provided interfaces are not matching";
    //-------------------------------------------------------------------------------------------------
    // REGISTER:
    // * register new device (expectation: 201, DeviceResponseDTO)
    // * register device with existing name and same properties (expectation: 200, DeviceResponseDTO)
    // * register device with existing name and different properties (expectation: 400, errorMessage: "Device with name '____' already exists, but provided metadata is not matching")
    // * register device with existing name and different addresses (expectation: 400, errorMessage: "Device with name '____' already exists, but provided interfaces are not matching")
    // * register device with existing name and same addresses but in a different order (expectation: 200, DeviceResponseDTO)
    
    //-------------------------------------------------------------------------------------------------
	@Test
	void testRegisterNewDevice() throws Exception {
		//fail("Not yet implemented");
		final String testDeviceDtoJson = Utilities.toJson(testDeviceDto);
		
		mockMvc.perform(post(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content(testDeviceDtoJson))
				.andExpect(status().isOk());
		//TODO: visszaadott dto-t is ellenorizni
	}
	
	// LOOKUP
	
	// REVOKE

}
