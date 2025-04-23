package eu.arrowhead.serviceorchestration;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.SystemInfo;
import eu.arrowhead.common.http.filter.authentication.AuthenticationPolicy;
import eu.arrowhead.common.model.ServiceModel;
import eu.arrowhead.common.model.SystemModel;

@Component(Constants.BEAN_NAME_SYSTEM_INFO)
public class SimpleServiceOrchestrationSystemInfo extends SystemInfo {

	//=================================================================================================
	// members

	private SystemModel systemModel;

	//=================================================================================================
	// methods
	
	@Override
	//-------------------------------------------------------------------------------------------------
	public String getSystemName() {
		return SimpleServiceOrchestrationConstants.SYSTEM_NAME;
	}

	@Override
	//-------------------------------------------------------------------------------------------------
	public SystemModel getSystemModel() {
		if (systemModel == null) {
			SystemModel.Builder builder = new SystemModel.Builder()
					.address(getAddress())
					.version(Constants.AH_FRAMEWORK_VERSION);

			if (AuthenticationPolicy.CERTIFICATE == this.getAuthenticationPolicy()) {
				builder = builder.metadata(Constants.METADATA_KEY_X509_PUBLIC_KEY, getPublicKey());
			}

			systemModel = builder.build();
		}

		return systemModel;
	}

	@Override
	//-------------------------------------------------------------------------------------------------
	public List<ServiceModel> getServices() {
		return null;
	}
	
	//=================================================================================================
	// assistant methods

	@Override
	//-------------------------------------------------------------------------------------------------
	protected PublicConfigurationKeysAndDefaults getPublicConfigurationKeysAndDefaults() {
		return new PublicConfigurationKeysAndDefaults(
				Set.of(Constants.SERVER_ADDRESS,
						Constants.SERVER_PORT,
						Constants.MQTT_API_ENABLED,
						Constants.DOMAIN_NAME,
						Constants.AUTHENTICATION_POLICY,
						Constants.ENABLE_MANAGEMENT_FILTER,
						Constants.MANAGEMENT_POLICY,
						Constants.ENABLE_BLACKLIST_FILTER,
						Constants.FORCE_BLACKLIST_FILTER,
						Constants.ALLOW_SELF_ADDRESSING,
						Constants.ALLOW_NON_ROUTABLE_ADDRESSING,
						Constants.MAX_PAGE_SIZE
						// Constants.SERVICE_ADDRESS_ALIAS ?
						),
				SimpleServiceOrchestrationDefaults.class);
	}
}
