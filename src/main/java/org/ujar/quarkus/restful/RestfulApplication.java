package org.ujar.quarkus.restful;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.auth.LoginConfig;

/**
 * Top level entry point for the REST services.
 */
@LoginConfig(authMethod = "MP-JWT", realmName = "ujar")
@ApplicationPath("/")
public class RestfulApplication extends Application {
}
