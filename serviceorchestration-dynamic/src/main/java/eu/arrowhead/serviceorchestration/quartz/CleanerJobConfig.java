package eu.arrowhead.serviceorchestration.quartz;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;

import eu.arrowhead.serviceorchestration.DynamicServiceOrchestrationConstants;
import jakarta.annotation.PostConstruct;

@Configuration
@EnableAutoConfiguration
public class CleanerJobConfig {

	//=================================================================================================
	// members

	@Value(DynamicServiceOrchestrationConstants.$CLEANER_JOB_INTERVAL_WD)
	private int interval;

	private final Logger logger = LogManager.getLogger(this.getClass());

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@Bean(DynamicServiceOrchestrationConstants.CLEANER_JOB)
	JobDetailFactoryBean cleanerJobDetail() {
		final JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
		jobDetailFactory.setJobClass(CleanerJob.class);
		jobDetailFactory.setDescription("Removing expired subscriptions and service locks");
		jobDetailFactory.setDurability(true);
		return jobDetailFactory;
	}

	//-------------------------------------------------------------------------------------------------
	@Bean(DynamicServiceOrchestrationConstants.CLEANER_TRIGGER)
	SimpleTriggerFactoryBean cleanerTrigger(@Qualifier(DynamicServiceOrchestrationConstants.CLEANER_JOB) final JobDetail job) {
		final SimpleTriggerFactoryBean trigger = new SimpleTriggerFactoryBean();
		trigger.setJobDetail(job);
		trigger.setRepeatInterval(interval);
		trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
		trigger.setStartDelay(interval);
		return trigger;
	}

	//-------------------------------------------------------------------------------------------------
	@PostConstruct
	public void init() {
		logger.info("Cleaner job is initialized.");
	}
}
