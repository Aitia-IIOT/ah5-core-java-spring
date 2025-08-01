package eu.arrowhead.authorization.service.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Pair;

import eu.arrowhead.authorization.service.dto.NormalizedAuthorizationPolicyRequest;
import eu.arrowhead.authorization.service.dto.NormalizedGrantRequest;
import eu.arrowhead.authorization.service.normalization.AuthorizationPolicyInstanceIdentifierNormalizer;
import eu.arrowhead.authorization.service.normalization.AuthorizationPolicyRequestNormalizer;
import eu.arrowhead.authorization.service.normalization.AuthorizationScopeNormalizer;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.common.service.validation.cloud.CloudIdentifierNormalizer;
import eu.arrowhead.common.service.validation.cloud.CloudIdentifierValidator;
import eu.arrowhead.common.service.validation.name.EventTypeNameNormalizer;
import eu.arrowhead.common.service.validation.name.EventTypeNameValidator;
import eu.arrowhead.common.service.validation.name.ServiceDefinitionNameNormalizer;
import eu.arrowhead.common.service.validation.name.ServiceDefinitionNameValidator;
import eu.arrowhead.common.service.validation.name.SystemNameNormalizer;
import eu.arrowhead.common.service.validation.name.SystemNameValidator;
import eu.arrowhead.dto.AuthorizationGrantRequestDTO;
import eu.arrowhead.dto.AuthorizationLookupRequestDTO;
import eu.arrowhead.dto.AuthorizationPolicyRequestDTO;
import eu.arrowhead.dto.enums.AuthorizationLevel;
import eu.arrowhead.dto.enums.AuthorizationPolicyType;
import eu.arrowhead.dto.enums.AuthorizationTargetType;

@ExtendWith(MockitoExtension.class)
public class AuthorizationValidationTest {

	//=================================================================================================
	// members

	@InjectMocks
	private AuthorizationValidation validator;

	@Mock
	private SystemNameValidator systemNameValidator;

	@Mock
	private SystemNameNormalizer systemNameNormalizer;

	@Mock
	private CloudIdentifierValidator cloudIdentifierValidator;

	@Mock
	private CloudIdentifierNormalizer cloudIdentifierNormalizer;

	@Mock
	private ServiceDefinitionNameValidator serviceDefNameValidator;

	@Mock
	private ServiceDefinitionNameNormalizer serviceDefNameNormalizer;

	@Mock
	private EventTypeNameValidator eventTypeNameValidator;

	@Mock
	private EventTypeNameNormalizer eventTypeNameNormalizer;

	@Mock
	private AuthorizationPolicyRequestValidator policyRequestValidator;

	@Mock
	private AuthorizationPolicyRequestNormalizer policyRequestNormalizer;

	@Mock
	private AuthorizationScopeValidator scopeValidator;

	@Mock
	private AuthorizationScopeNormalizer scopeNormalizer;

	@Mock
	private AuthorizationPolicyInstanceIdentifierValidator instanceIdValidator;

	@Mock
	private AuthorizationPolicyInstanceIdentifierNormalizer instanceIdNormalizer;

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeSystemNameOriginNull() {
		final Throwable ex = assertThrows(IllegalArgumentException.class,
				() -> validator.validateAndNormalizeSystemName("ProviderName", null));

		assertEquals("origin is empty", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeSystemNameEmpty() {
		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeSystemName("", "testOrigin"));

		assertEquals("System name is empty", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeSystemNameExceptionInValidator() {
		when(systemNameNormalizer.normalize("ProviderName")).thenReturn("ProviderName");
		doThrow(new InvalidParameterException("test")).when(systemNameValidator).validateSystemName("ProviderName");

		final ArrowheadException ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeSystemName("ProviderName", "testOrigin"));

		verify(systemNameNormalizer).normalize("ProviderName");
		verify(systemNameValidator).validateSystemName("ProviderName");

		assertEquals("test", ex.getMessage());
		assertEquals("testOrigin", ex.getOrigin());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeSystemNameOk() {
		when(systemNameNormalizer.normalize("ProviderName")).thenReturn("ProviderName");
		doNothing().when(systemNameValidator).validateSystemName("ProviderName");

		final String result = validator.validateAndNormalizeSystemName("ProviderName", "testOrigin");

		final InOrder orderVerifier = Mockito.inOrder(systemNameNormalizer, systemNameValidator);
		orderVerifier.verify(systemNameNormalizer).normalize("ProviderName");
		orderVerifier.verify(systemNameValidator).validateSystemName("ProviderName");

		assertEquals("ProviderName", result);
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeGrantRequestOriginNull() {
		final AuthorizationGrantRequestDTO request = new AuthorizationGrantRequestDTO(null, null, null, null, null, null);

		final Throwable ex = assertThrows(IllegalArgumentException.class,
				() -> validator.validateAndNormalizeGrantRequest("ProviderName", request, null));

		assertEquals("origin is empty", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeGrantRequestNullRequest() {
		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeGrantRequest("ProviderName", null, "testOrigin"));

		assertEquals("Request payload is missing", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeGrantRequestEmptyTargetType() {
		final AuthorizationGrantRequestDTO request = new AuthorizationGrantRequestDTO(null, null, null, null, null, null);

		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeGrantRequest("ProviderName", request, "testOrigin"));

		assertEquals("Target type is missing", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeGrantRequestInvalidTargetType() {
		final AuthorizationGrantRequestDTO request = new AuthorizationGrantRequestDTO(null, "invalid", null, null, null, null);

		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeGrantRequest("ProviderName", request, "testOrigin"));

		assertEquals("Target type is invalid: invalid", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeGrantRequestTargetNull() {
		final AuthorizationGrantRequestDTO request = new AuthorizationGrantRequestDTO.Builder(AuthorizationTargetType.SERVICE_DEF)
				.build();

		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeGrantRequest("ProviderName", request, "testOrigin"));

		assertEquals("Target is missing", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeGrantRequestExceptionInAValidator() {
		final AuthorizationPolicyRequestDTO defaultPolicy = new AuthorizationPolicyRequestDTO.Builder(AuthorizationPolicyType.ALL)
				.build();
		final AuthorizationGrantRequestDTO request = new AuthorizationGrantRequestDTO.Builder(AuthorizationTargetType.SERVICE_DEF)
				.cloud("invalid")
				.target("testService")
				.defaultPolicy(defaultPolicy)
				.build();

		doNothing().when(policyRequestValidator).validateAuthorizationPolicy(defaultPolicy, true, "testOrigin");
		when(cloudIdentifierNormalizer.normalize("invalid")).thenReturn("Invalid");
		when(serviceDefNameNormalizer.normalize("testService")).thenReturn("testService");
		when(policyRequestNormalizer.normalize(defaultPolicy)).thenReturn(new NormalizedAuthorizationPolicyRequest(AuthorizationPolicyType.ALL, null, null));
		doThrow(new InvalidParameterException("test")).when(cloudIdentifierValidator).validateCloudIdentifier("Invalid");

		final ArrowheadException ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeGrantRequest("ProviderName", request, "testOrigin"));

		final InOrder policyRequestOrderValidator = Mockito.inOrder(policyRequestValidator, policyRequestNormalizer);
		final InOrder cloudOrderValidator = Mockito.inOrder(cloudIdentifierNormalizer, cloudIdentifierValidator);

		policyRequestOrderValidator.verify(policyRequestValidator).validateAuthorizationPolicy(defaultPolicy, true, "testOrigin");
		cloudOrderValidator.verify(cloudIdentifierNormalizer).normalize("invalid");
		verify(serviceDefNameNormalizer).normalize("testService");
		verify(eventTypeNameNormalizer, never()).normalize("testService");
		policyRequestOrderValidator.verify(policyRequestNormalizer).normalize(defaultPolicy);
		cloudOrderValidator.verify(cloudIdentifierValidator).validateCloudIdentifier("Invalid");

		assertEquals("test", ex.getMessage());
		assertEquals("testOrigin", ex.getOrigin());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeGrantRequestServiceDefOk() {
		final AuthorizationPolicyRequestDTO defaultPolicy = new AuthorizationPolicyRequestDTO.Builder(AuthorizationPolicyType.ALL)
				.build();
		final AuthorizationPolicyRequestDTO scopedPolicy = new AuthorizationPolicyRequestDTO.Builder(AuthorizationPolicyType.BLACKLIST)
				.addBlacklistElement("BadSystem")
				.build();
		final AuthorizationGrantRequestDTO request = new AuthorizationGrantRequestDTO(
				null,
				"SERVICE_DEF",
				"testService",
				"description",
				defaultPolicy,
				Map.of("op", scopedPolicy));

		final NormalizedAuthorizationPolicyRequest nScopedPolicy = new NormalizedAuthorizationPolicyRequest(AuthorizationPolicyType.BLACKLIST, List.of("BadSystem"), null);
		final NormalizedAuthorizationPolicyRequest nDefaultPolicy = new NormalizedAuthorizationPolicyRequest(AuthorizationPolicyType.ALL, null, null);

		doNothing().when(policyRequestValidator).validateAuthorizationPolicy(defaultPolicy, true, "testOrigin");
		doNothing().when(policyRequestValidator).validateAuthorizationPolicy(scopedPolicy, false, "testOrigin");
		when(serviceDefNameNormalizer.normalize("testService")).thenReturn("testService");
		when(scopeNormalizer.normalize("op")).thenReturn("op");
		when(policyRequestNormalizer.normalize(scopedPolicy)).thenReturn(nScopedPolicy);
		when(policyRequestNormalizer.normalize(defaultPolicy)).thenReturn(nDefaultPolicy);
		doNothing().when(cloudIdentifierValidator).validateCloudIdentifier("LOCAL");
		doNothing().when(systemNameValidator).validateSystemName("ProviderName");
		doNothing().when(serviceDefNameValidator).validateServiceDefinitionName("testService");
		doNothing().when(scopeValidator).validateScope("op");
		doNothing().when(policyRequestValidator).validateNormalizedAuthorizationPolicy(nScopedPolicy);
		doNothing().when(scopeValidator).validateScope("*");
		doNothing().when(policyRequestValidator).validateNormalizedAuthorizationPolicy(nDefaultPolicy);

		final NormalizedGrantRequest result = validator.validateAndNormalizeGrantRequest("ProviderName", request, "testOrigin");

		final InOrder policyRequestOrderValidator = Mockito.inOrder(policyRequestValidator, policyRequestNormalizer);
		final InOrder serviceDefOrderValidator = Mockito.inOrder(serviceDefNameNormalizer, serviceDefNameValidator);
		final InOrder scopeOrderValidator = Mockito.inOrder(scopeNormalizer, scopeValidator);

		policyRequestOrderValidator.verify(policyRequestValidator).validateAuthorizationPolicy(defaultPolicy, true, "testOrigin");
		policyRequestOrderValidator.verify(policyRequestValidator).validateAuthorizationPolicy(scopedPolicy, false, "testOrigin");
		serviceDefOrderValidator.verify(serviceDefNameNormalizer).normalize("testService");
		verify(eventTypeNameNormalizer, never()).normalize("testService");
		scopeOrderValidator.verify(scopeNormalizer).normalize("op");
		policyRequestOrderValidator.verify(policyRequestNormalizer).normalize(scopedPolicy);
		policyRequestOrderValidator.verify(policyRequestNormalizer).normalize(defaultPolicy);
		verify(cloudIdentifierValidator).validateCloudIdentifier("LOCAL");
		verify(systemNameValidator).validateSystemName("ProviderName");
		serviceDefOrderValidator.verify(serviceDefNameValidator).validateServiceDefinitionName("testService");
		verify(eventTypeNameValidator, never()).validateEventTypeName("testService");
		scopeOrderValidator.verify(scopeValidator).validateScope("op");
		policyRequestOrderValidator.verify(policyRequestValidator).validateNormalizedAuthorizationPolicy(nScopedPolicy);
		scopeOrderValidator.verify(scopeValidator).validateScope("*");
		policyRequestOrderValidator.verify(policyRequestValidator).validateNormalizedAuthorizationPolicy(nDefaultPolicy);

		assertEquals(AuthorizationLevel.PROVIDER, result.level());
		assertEquals("LOCAL", result.cloud());
		assertEquals("ProviderName", result.provider());
		assertEquals(AuthorizationTargetType.SERVICE_DEF, result.targetType());
		assertEquals("testService", result.target());
		assertEquals("description", result.description());
		assertEquals(2, result.policies().size());
		assertEquals(nDefaultPolicy, result.policies().get("*"));
		assertEquals(nScopedPolicy, result.policies().get("op"));
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeGrantRequestEventTypOk() {
		final AuthorizationPolicyRequestDTO defaultPolicy = new AuthorizationPolicyRequestDTO.Builder(AuthorizationPolicyType.ALL)
				.build();
		final AuthorizationGrantRequestDTO request = new AuthorizationGrantRequestDTO.Builder(AuthorizationTargetType.EVENT_TYPE)
				.target("testEvent")
				.defaultPolicy(defaultPolicy)
				.build();
		final NormalizedAuthorizationPolicyRequest nDefaultPolicy = new NormalizedAuthorizationPolicyRequest(AuthorizationPolicyType.ALL, null, null);

		doNothing().when(policyRequestValidator).validateAuthorizationPolicy(defaultPolicy, true, "testOrigin");
		when(eventTypeNameNormalizer.normalize("testEvent")).thenReturn("testEvent");
		when(policyRequestNormalizer.normalize(defaultPolicy)).thenReturn(nDefaultPolicy);
		doNothing().when(cloudIdentifierValidator).validateCloudIdentifier("LOCAL");
		doNothing().when(systemNameValidator).validateSystemName("ProviderName");
		doNothing().when(eventTypeNameValidator).validateEventTypeName("testEvent");
		doNothing().when(scopeValidator).validateScope("*");
		doNothing().when(policyRequestValidator).validateNormalizedAuthorizationPolicy(nDefaultPolicy);

		final NormalizedGrantRequest result = validator.validateAndNormalizeGrantRequest("ProviderName", request, "testOrigin");

		final InOrder policyRequestOrderValidator = Mockito.inOrder(policyRequestValidator, policyRequestNormalizer);
		final InOrder eventTypeOrderValidator = Mockito.inOrder(eventTypeNameNormalizer, eventTypeNameValidator);

		policyRequestOrderValidator.verify(policyRequestValidator).validateAuthorizationPolicy(defaultPolicy, true, "testOrigin");
		verify(serviceDefNameNormalizer, never()).normalize("testEvent");
		eventTypeOrderValidator.verify(eventTypeNameNormalizer).normalize("testEvent");
		policyRequestOrderValidator.verify(policyRequestNormalizer).normalize(defaultPolicy);
		verify(cloudIdentifierValidator).validateCloudIdentifier("LOCAL");
		verify(systemNameValidator).validateSystemName("ProviderName");
		verify(serviceDefNameValidator, never()).validateServiceDefinitionName("testEvent");
		eventTypeOrderValidator.verify(eventTypeNameValidator).validateEventTypeName("testEvent");
		verify(scopeValidator).validateScope("*");
		policyRequestOrderValidator.verify(policyRequestValidator).validateNormalizedAuthorizationPolicy(nDefaultPolicy);

		assertEquals(AuthorizationLevel.PROVIDER, result.level());
		assertEquals("LOCAL", result.cloud());
		assertEquals("ProviderName", result.provider());
		assertEquals(AuthorizationTargetType.EVENT_TYPE, result.targetType());
		assertEquals("testEvent", result.target());
		assertEquals(1, result.policies().size());
		assertEquals(nDefaultPolicy, result.policies().get("*"));
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeRevokeInputOriginEmpty() {
		final Throwable ex = assertThrows(IllegalArgumentException.class,
				() -> validator.validateAndNormalizeRevokeInput("ProviderName", "PR|LOCAL|ProviderName|SERVICE_DEF|testService", ""));

		assertEquals("origin is empty", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeRevokeInputInstanceIdNull() {
		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeRevokeInput("ProviderName", null, "testOrigin"));

		assertEquals("Instance id is missing", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeRevokeInputExceptionInAValidator() {
		when(systemNameNormalizer.normalize("ProviderName")).thenReturn("ProviderName");
		when(instanceIdNormalizer.normalize("PR|LOCAL|ProviderName|SERVICE_DEF|testService")).thenReturn("PR|LOCAL|ProviderName|SERVICE_DEF|testService");
		doThrow(new InvalidParameterException("test")).when(systemNameValidator).validateSystemName("ProviderName");

		final ArrowheadException ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeRevokeInput("ProviderName", "PR|LOCAL|ProviderName|SERVICE_DEF|testService", "testOrigin"));

		verify(systemNameNormalizer).normalize("ProviderName");
		verify(instanceIdNormalizer).normalize("PR|LOCAL|ProviderName|SERVICE_DEF|testService");
		verify(systemNameValidator).validateSystemName("ProviderName");

		assertEquals("test", ex.getMessage());
		assertEquals("testOrigin", ex.getOrigin());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeRevokeInputOk() {
		when(systemNameNormalizer.normalize("ProviderName")).thenReturn("ProviderName");
		when(instanceIdNormalizer.normalize("PR|LOCAL|ProviderName|SERVICE_DEF|testService")).thenReturn("PR|LOCAL|ProviderName|SERVICE_DEF|testService");
		doNothing().when(systemNameValidator).validateSystemName("ProviderName");
		doNothing().when(instanceIdValidator).validateInstanceIdentifier("PR|LOCAL|ProviderName|SERVICE_DEF|testService");

		final Pair<String, String> result = validator.validateAndNormalizeRevokeInput("ProviderName", "PR|LOCAL|ProviderName|SERVICE_DEF|testService", "testOrigin");

		final InOrder systemNameOrderValidator = Mockito.inOrder(systemNameNormalizer, systemNameValidator);
		final InOrder instanceIdOrderValidator = Mockito.inOrder(instanceIdNormalizer, instanceIdValidator);

		systemNameOrderValidator.verify(systemNameNormalizer).normalize("ProviderName");
		instanceIdOrderValidator.verify(instanceIdNormalizer).normalize("PR|LOCAL|ProviderName|SERVICE_DEF|testService");
		systemNameOrderValidator.verify(systemNameValidator).validateSystemName("ProviderName");
		instanceIdOrderValidator.verify(instanceIdValidator).validateInstanceIdentifier("PR|LOCAL|ProviderName|SERVICE_DEF|testService");

		assertNotNull(result);
		assertEquals("ProviderName", result.getFirst());
		assertEquals("PR|LOCAL|ProviderName|SERVICE_DEF|testService", result.getSecond());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeLookupRequestOriginNull() {
		final AuthorizationLookupRequestDTO request = new AuthorizationLookupRequestDTO(null, null, null, null);

		final Throwable ex = assertThrows(IllegalArgumentException.class,
				() -> validator.validateAndNormalizeLookupRequest("ProviderName", request, null));

		assertEquals("origin is empty", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeLookupRequestNullRequest() {
		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeLookupRequest("ProviderName", null, "testOrigin"));

		assertEquals("Request payload is missing", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeLookupRequestMissingMandatoryFilter() {
		final AuthorizationLookupRequestDTO request = new AuthorizationLookupRequestDTO(null, null, null, null);

		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeLookupRequest("ProviderName", request, "testOrigin"));

		assertEquals("One of the following filters must be used: 'instanceIds', 'targetNames', 'cloudIdentifiers'", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeLookupRequestInstanceIdFilterContainsNull() {
		final List<String> instanceIds = new ArrayList<>(1);
		instanceIds.add(null);

		final AuthorizationLookupRequestDTO request = new AuthorizationLookupRequestDTO(
				instanceIds,
				null,
				null,
				null);

		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeLookupRequest("ProviderName", request, "testOrigin"));

		assertEquals("Instance id list contains null or empty element", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeLookupRequestTargetNameFilterContainsEmpty() {
		final AuthorizationLookupRequestDTO request = new AuthorizationLookupRequestDTO(
				null,
				null,
				List.of(""),
				null);

		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeLookupRequest("ProviderName", request, "testOrigin"));

		assertEquals("Target names list contains null or empty element", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeLookupRequestTargetTypeIsMandatoryCase() {
		final AuthorizationLookupRequestDTO request = new AuthorizationLookupRequestDTO(
				List.of("PR|LOCAL|ProviderName|SERVICE_DEF|testService"),
				null,
				List.of("testService"),
				null);

		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeLookupRequest("ProviderName", request, "testOrigin"));

		assertEquals("If target names list is specified then a valid target type is mandatory", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeLookupRequestCloudIdentifiersFilterContainsNull() {
		final List<String> cloudIds = new ArrayList<>(1);
		cloudIds.add(null);

		final AuthorizationLookupRequestDTO request = new AuthorizationLookupRequestDTO(
				null,
				cloudIds,
				List.of("testService"),
				"SERVICE_DEF");

		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeLookupRequest("ProviderName", request, "testOrigin"));

		assertEquals("Cloud identifiers list contains null or empty element", ex.getMessage());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void testValidateAndNormalizeLookupRequestInvalidTargetType() {

		final AuthorizationLookupRequestDTO request = new AuthorizationLookupRequestDTO(
				null,
				List.of("LOCAL"),
				null,
				"invalid");

		final Throwable ex = assertThrows(InvalidParameterException.class,
				() -> validator.validateAndNormalizeLookupRequest("ProviderName", request, "testOrigin"));

		assertEquals("Target type is invalid: invalid", ex.getMessage());
	}
}