package io.github.brawaru.componential;

/**
 * Represents a function to call for components unload
 */
public interface DeinitializationCall {
    /**
     * Method to call for all previously registered and activated components to unload
     * @throws ComponentRelatedException If unexpected error occurred during the components unload
     */
    void deinitialize() throws ComponentRelatedException;
}
