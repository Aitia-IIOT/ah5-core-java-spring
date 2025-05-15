package eu.arrowhead.serviceorchestration.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import eu.arrowhead.common.jpa.RefreshableRepository;
import eu.arrowhead.serviceorchestration.jpa.entity.OrchestrationStore;

@Repository
public interface OrchestrationStoreRepository extends RefreshableRepository<OrchestrationStore, UUID> {
	
	//-------------------------------------------------------------------------------------------------
	public List<OrchestrationStore> findAllByConsumerAndServiceInstanceIdAndPriority(
			final String consumer, final String serviceInstanceId, final int priority);

	//-------------------------------------------------------------------------------------------------
	public List<OrchestrationStore> findAllByConsumerIn(final List<String> consumerNames);

	//-------------------------------------------------------------------------------------------------
	public List<OrchestrationStore> findAllByServiceDefinitionIn(final List<String> serviceDefinitionNames);

	//-------------------------------------------------------------------------------------------------
	public List<OrchestrationStore> findAllByServiceInstanceIdIn(final List<String> serviceInstanceIds);

	//-------------------------------------------------------------------------------------------------
	public List<OrchestrationStore> findAllByCreatedBy(final String createdBy);

	//-------------------------------------------------------------------------------------------------
	public Page<OrchestrationStore> findAllByIdIn(final List<UUID> matchingIds, final PageRequest pagination);
	
}
