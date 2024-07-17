package eu.arrowhead.serviceregistry.api.http;

import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.Constants;
import eu.arrowhead.dto.DeviceRequestDTO;
import eu.arrowhead.dto.DeviceResponseDTO;
import eu.arrowhead.dto.ErrorMessageDTO;
import eu.arrowhead.serviceregistry.ServiceRegistryConstants;
import eu.arrowhead.serviceregistry.service.DeviceDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping(ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class DeviceDiscoveryAPI {

	// TODO: implement the following endpoints (all endpoints are public)

	//=================================================================================================
	// members

	@Autowired
	private DeviceDiscoveryService ddService;

	private final Logger logger = LogManager.getLogger(this.getClass());

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@Operation(summary = "Returns the newly created or matching device entry")
	@ApiResponses(value = {
			@ApiResponse(responseCode = Constants.HTTP_STATUS_CREATED, description = Constants.SWAGGER_HTTP_201_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DeviceResponseDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_OK, description = Constants.SWAGGER_HTTP_200_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DeviceResponseDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_BAD_REQUEST, description = Constants.SWAGGER_HTTP_400_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_UNAUTHORIZED, description = Constants.SWAGGER_HTTP_401_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_FORBIDDEN, description = Constants.SWAGGER_HTTP_403_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_INTERNAL_SERVER_ERROR, description = Constants.SWAGGER_HTTP_500_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) })
	})
	@PostMapping(path = ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DeviceResponseDTO> register(@RequestBody final DeviceRequestDTO dto) {
		logger.debug("register started");

		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REGISTER_PATH;

		final Entry<DeviceResponseDTO, Boolean> result = ddService.registerDevice(dto, origin);
		return new ResponseEntity<DeviceResponseDTO>(result.getKey(), result.getValue() ? HttpStatus.CREATED : HttpStatus.OK);
	}

	// lookup-device operation: POST /lookup (200)

	// revoke-device operation: DELETE /revoke (200/204 depending on actual delete)
	// you can delete not-existing device => nothing happens

	//-------------------------------------------------------------------------------------------------
	@Operation(summary = "Delets device entry by name if exists")
	@ApiResponses(value = {
			@ApiResponse(responseCode = Constants.HTTP_STATUS_OK, description = Constants.SWAGGER_HTTP_200_MESSAGE),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_NO_CONTENT, description = Constants.SWAGGER_HTTP_204_MESSAGE),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_BAD_REQUEST, description = Constants.SWAGGER_HTTP_400_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_UNAUTHORIZED, description = Constants.SWAGGER_HTTP_401_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_FORBIDDEN, description = Constants.SWAGGER_HTTP_403_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_INTERNAL_SERVER_ERROR, description = Constants.SWAGGER_HTTP_500_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) })
	})
	@DeleteMapping(path = ServiceRegistryConstants.HTTP_API_OP_REVOKE_PATH)
	public ResponseEntity<Void> revoke(final String name) {
		logger.debug("revoke started");

		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_DEVICE_DISCOVERY_PATH + ServiceRegistryConstants.HTTP_API_OP_REVOKE_PATH;

		final boolean result = ddService.revokeDevice(name, origin);
		return new ResponseEntity<Void>(result ? HttpStatus.OK : HttpStatus.NO_CONTENT);
	}
}
