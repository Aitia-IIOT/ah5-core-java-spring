/*******************************************************************************
 *
 * Copyright (c) 2025 AITIA
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 *
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  	AITIA - implementation
 *  	Arrowhead Consortia - conceptualization
 *
 *******************************************************************************/
package eu.arrowhead.serviceregistry.service.dto;

import java.util.List;
import java.util.Map;

import eu.arrowhead.dto.AddressDTO;

public record NormalizedSystemRequestDTO(
		String name,
		Map<String, Object> metadata,
		String version,
		List<AddressDTO> addresses,
		String deviceName) {
}