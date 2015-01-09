package org.jboss.shrinkwrap.resolver.api;

import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.robovm.maven.resolver.RoboVMResolver;

/**
 * This class is a workaround for problem that emerged when using vanilla RoboVMResolver.
 *
 * Somewhere in 'Maven.configureResolver()' is reflective loading of class,
 * using the default class loader, which does not know about any plugin dependencies, so it fails.
 * That is because SBT seems to use different class loader for plugin's files.
 *
 * Method configureResolver() defined here takes care of supplying correct class loader.
 *
 * It is in this weird package because ResolverSystemFactory.createFromUserView() is package private, so we don't have
 * to use reflection.
 * There are methods like Resolvers.configure, which would work instead,
 * but those are unfortunately buggy - ignore supplied classloader, which is the whole point of this workaround.
 *
 * TODO: Move to Resolvers.configure as soon as it is fixed.
 *
 * Imagine how much fun was to debug this.
 */
public class SBTRoboVMResolver extends RoboVMResolver {

    private ConfigurableMavenResolverSystem configureResolver(){
        try {
            //SpiServiceLoader is class that was failing to load without workaround
            //but any class from this project & its dependencies should work in theory
            ClassLoader sbtPluginClassLoader = Class.forName("org.jboss.shrinkwrap.resolver.spi.loader.SpiServiceLoader").getClassLoader();
            //We could call Resolvers.configure(ConfigurableMavenResolverSystem.class, sbtPluginClassLoader) instead, but that is broken
            return ResolverSystemFactory.createFromUserView(ConfigurableMavenResolverSystem.class, sbtPluginClassLoader);
        } catch (ClassNotFoundException e) {
            System.out.println("Failed to apply SBTRoboVMResolver workaround. ");
            return Maven.configureResolver();
        }
    }

    //Those methods are same as original, but instead of Maven.configureResolver() they call configureResolver()

    public MavenResolvedArtifact resolveArtifact(String artifact) {
        try {
            /* do offline check first */
            return configureResolver().workOffline().resolve(artifact).withoutTransitivity().asSingleResolvedArtifact();
        } catch (NoResolvedResultException nre) {
            return configureResolver()
                    .withRemoteRepo("Sonatype Nexus Snapshots",
                            "https://oss.sonatype.org/content/repositories/snapshots/", "default")
                    .resolve(artifact).withoutTransitivity().asSingleResolvedArtifact();
        }
    }

    public MavenResolvedArtifact[] resolveArtifacts(String artifact) {
        try {
            /* do offline check first */
            return configureResolver().workOffline().resolve(artifact).withTransitivity().asResolvedArtifact();
        } catch (NoResolvedResultException nre) {
            return configureResolver()
                    .withRemoteRepo("Sonatype Nexus Snapshots",
                            "https://oss.sonatype.org/content/repositories/snapshots/", "default")
                    .resolve(artifact).withTransitivity().asResolvedArtifact();
        }
    }
}
