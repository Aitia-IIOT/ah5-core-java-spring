package eu.arrowhead.authorization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.arrowhead.authorization.http.filter.authorization.InternalManagementServiceFilter;
import eu.arrowhead.authorization.mqtt.filter.authorization.InternalManagementServiceMqttFilter;
import eu.arrowhead.common.Constants;
import eu.arrowhead.common.http.filter.ArrowheadFilter;
import eu.arrowhead.common.mqtt.filter.ArrowheadMqttFilter;

@Configuration
public class BeanConfig {

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@Bean
	@ConditionalOnProperty(name = Constants.ENABLE_MANAGEMENT_FILTER, matchIfMissing = false)
	ArrowheadFilter internalManagementServiceFilter() {
		return new InternalManagementServiceFilter();
	}

	//-------------------------------------------------------------------------------------------------
	@Bean
	@ConditionalOnProperty(name = { Constants.MQTT_API_ENABLED, Constants.ENABLE_MANAGEMENT_FILTER }, havingValue = "true", matchIfMissing = false)
	ArrowheadMqttFilter internalManagementServiceMqttFilter() {
		return new InternalManagementServiceMqttFilter();
	}
}