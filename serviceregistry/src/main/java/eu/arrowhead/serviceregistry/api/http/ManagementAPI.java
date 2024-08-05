package eu.arrowhead.serviceregistry.api.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.service.LogService;
import eu.arrowhead.dto.ErrorMessageDTO;
import eu.arrowhead.dto.LogEntryListResponseDTO;
import eu.arrowhead.dto.LogRequestDTO;
import eu.arrowhead.dto.ServiceDefinitionListRequestDTO;
import eu.arrowhead.dto.ServiceDefinitionListResponseDTO;
import eu.arrowhead.dto.SystemListRequestDTO;
import eu.arrowhead.dto.SystemListResponseDTO;
import eu.arrowhead.dto.SystemQueryRequestDTO;
import eu.arrowhead.serviceregistry.ServiceRegistryConstants;
import eu.arrowhead.serviceregistry.service.ManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping(ServiceRegistryConstants.HTTP_API_MANAGEMENT_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class ManagementAPI {

	// TODO: implement the following endpoints

	//=================================================================================================
	// members

	@Autowired
	private ManagementService mgmtService;

	@Autowired
	private LogService logService;

	private final Logger logger = LogManager.getLogger(this.getClass());

	//=================================================================================================
	// methods

	// LOG

	//-------------------------------------------------------------------------------------------------
	@Operation(summary = "Returns the log entries")
	@ApiResponses(value = {
			@ApiResponse(responseCode = Constants.HTTP_STATUS_OK, description = Constants.SWAGGER_HTTP_200_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = LogEntryListResponseDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_BAD_REQUEST, description = Constants.SWAGGER_HTTP_400_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_UNAUTHORIZED, description = Constants.SWAGGER_HTTP_401_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_FORBIDDEN, description = Constants.SWAGGER_HTTP_403_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_INTERNAL_SERVER_ERROR, description = Constants.SWAGGER_HTTP_500_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) })
	})
	@PostMapping(path = Constants.HTTP_API_OP_LOGS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody LogEntryListResponseDTO getLogEntries(@RequestBody final LogRequestDTO dto) {
		logger.debug("getLogEntries started...");

		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_MANAGEMENT_PATH + Constants.HTTP_API_OP_LOGS_PATH;
		return logService.getLogEntries(dto, origin);
	}

	// CONFIG

	// get-config GET /config(list of config keys as query params) -> Empty input list means all is required

	// DEVICES

	// query-devices POST /devices
	// * paging: page, size, direction, sort
	// * filter to: device name list, address list, address type, metadata requirement list

	// create-devices POST /devices(bulk)

	// update-devices PUT /devices(bulk) -> device name can't be changed

	// remove-devices DELETE /devices(list of device names as query params)

	// SYSTEMS

	// query-systems POST /systems (query param verbose)
	// * paging: page, size, direction, sort
	// * filter to: name list, metadata requirement list, version list, address list, address type, device name list
	//-------------------------------------------------------------------------------------------------
	@Operation(summary = "Returns the system entries according to the query request")
	@ApiResponses(value = {
			@ApiResponse(responseCode = Constants.HTTP_STATUS_OK, description = Constants.SWAGGER_HTTP_200_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SystemListResponseDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_OK, description = Constants.SWAGGER_HTTP_200_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SystemListResponseDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_BAD_REQUEST, description = Constants.SWAGGER_HTTP_400_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_UNAUTHORIZED, description = Constants.SWAGGER_HTTP_401_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_FORBIDDEN, description = Constants.SWAGGER_HTTP_403_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_INTERNAL_SERVER_ERROR, description = Constants.SWAGGER_HTTP_500_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) })
	})
	@PostMapping(path = ServiceRegistryConstants.HTTP_API_OP_SYSTEM_QUERY_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody SystemListResponseDTO querySystems(@RequestBody(required = false) final SystemQueryRequestDTO dto, 
			@Parameter(
					name =  "verbose",
					description  = "Set true if you want the response to contain device details. (It should be configured in the Application properties as well.)",
					example = "true") 
			@RequestParam boolean verbose) {
		logger.debug("querySystems started, verbose = " + Boolean.toString(verbose));

		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_MANAGEMENT_PATH + ServiceRegistryConstants.HTTP_API_OP_SYSTEM_QUERY_PATH;
		
		return mgmtService.querySystems(dto, verbose, origin);
	}
	
	// create-systems POST /systems(bulk)
	@Operation(summary = "Returns the created system entries")
	@ApiResponses(value = {
			@ApiResponse(responseCode = Constants.HTTP_STATUS_CREATED, description = Constants.SWAGGER_HTTP_201_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SystemListResponseDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_BAD_REQUEST, description = Constants.SWAGGER_HTTP_400_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_UNAUTHORIZED, description = Constants.SWAGGER_HTTP_401_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_FORBIDDEN, description = Constants.SWAGGER_HTTP_403_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_INTERNAL_SERVER_ERROR, description = Constants.SWAGGER_HTTP_500_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) })
	})
	@ResponseStatus(code = HttpStatus.CREATED)
	@PostMapping(path = ServiceRegistryConstants.HTTP_API_OP_SYSTEM_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody SystemListResponseDTO createSystems(@RequestBody final SystemListRequestDTO dto) {
		logger.debug("createSystems started");

		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_MANAGEMENT_PATH + ServiceRegistryConstants.HTTP_API_OP_SYSTEM_PATH;
		return mgmtService.createSystems(dto, origin);
	}

	// update-systems PUT /systems(bulk) -> system name can't be changed
	//-------------------------------------------------------------------------------------------------
	@Operation(summary = "Returns the updated system entries")
	@ApiResponses(value = {
			@ApiResponse(responseCode = Constants.HTTP_STATUS_OK, description = Constants.SWAGGER_HTTP_200_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SystemListResponseDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_BAD_REQUEST, description = Constants.SWAGGER_HTTP_400_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_UNAUTHORIZED, description = Constants.SWAGGER_HTTP_401_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_FORBIDDEN, description = Constants.SWAGGER_HTTP_403_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_INTERNAL_SERVER_ERROR, description = Constants.SWAGGER_HTTP_500_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) })
	})
	@PutMapping(path = ServiceRegistryConstants.HTTP_API_OP_SYSTEM_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody SystemListResponseDTO updateSystems(@RequestBody final SystemListRequestDTO dto) {
		logger.debug("updateSystems started");

		final String origin = HttpMethod.PUT.name() + " " + ServiceRegistryConstants.HTTP_API_MANAGEMENT_PATH + ServiceRegistryConstants.HTTP_API_OP_SYSTEM_PATH;
		return mgmtService.updateSystems(dto, origin);
	}

	// remove-systems DELETE /systems(list of system names as query params)

	// SERVICES DEFINITIONS

	// get-service-definitions GET /service-definitions

	//-------------------------------------------------------------------------------------------------
	@Operation(summary = "Returns the created service definition entries")
	@ApiResponses(value = {
			@ApiResponse(responseCode = Constants.HTTP_STATUS_CREATED, description = Constants.SWAGGER_HTTP_201_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ServiceDefinitionListResponseDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_BAD_REQUEST, description = Constants.SWAGGER_HTTP_400_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_UNAUTHORIZED, description = Constants.SWAGGER_HTTP_401_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_FORBIDDEN, description = Constants.SWAGGER_HTTP_403_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) }),
			@ApiResponse(responseCode = Constants.HTTP_STATUS_INTERNAL_SERVER_ERROR, description = Constants.SWAGGER_HTTP_500_MESSAGE, content = {
					@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorMessageDTO.class)) })
	})
	@ResponseStatus(code = HttpStatus.CREATED)
	@PostMapping(path = ServiceRegistryConstants.HTTP_API_OP_SERVICE_DEFINITION_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody ServiceDefinitionListResponseDTO createServiceDefinitions(@RequestBody final ServiceDefinitionListRequestDTO dto) {
		logger.debug("createServiceDefinitions started");

		final String origin = HttpMethod.POST.name() + " " + ServiceRegistryConstants.HTTP_API_MANAGEMENT_PATH + ServiceRegistryConstants.HTTP_API_OP_SERVICE_DEFINITION_PATH;
		return mgmtService.createServiceDefinitions(dto, origin);

	}

	// remove-service-definitions DELETE /service-definitions(list of service definition names as query params)

	// SERVICE INSTANCES

	// query-service-instances POST /service-instances (query param verbose)
	// * paging: page, size, direction, sort
	// * filter to: instance id list, system name list. service def list, version list, aliveAt, metadata requirement list, interface name list, policy list

	// create-service-instances POST /service-instances(bulk)

	// update-service-instances PUT /service-instances(bulk) -> only metadata, expiresAt and interface can be changed

	// remove-service-instances DELETE /service-instances(list of service instance ids as query params)

	// INTERFACE TEMPLATES

	// query-interface-templates POST /interface-templates
	// * paging: page, size, direction, sort
	// * filter to: name list, protocol list

	// create-interface-templates POST /interface-templates

	// remove-interface-templates DELETE /interface-templates(list of interface template names as query params)
}
