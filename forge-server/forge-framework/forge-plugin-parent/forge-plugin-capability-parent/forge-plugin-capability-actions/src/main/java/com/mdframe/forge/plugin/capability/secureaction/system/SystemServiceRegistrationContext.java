package com.mdframe.forge.plugin.capability.secureaction.system;

/** Optional, server-validated scope for on-demand registration source queries. */
public record SystemServiceRegistrationContext(Long applicationId, Long objectId) { }
