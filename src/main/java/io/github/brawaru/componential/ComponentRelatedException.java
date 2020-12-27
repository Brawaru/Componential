package io.github.brawaru.componential;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an extension related to the component
 */
public class ComponentRelatedException extends Exception {
    private static final long serialVersionUID = 2719092338173634003L;

    private final String componentName;

    private final transient Component<? extends JavaPlugin> component;

    /**
     * Constructs a new exception linked to specific component with the specified detail message and cause
     * @param component component which caused this extension to occur
     * @param message the detail message
     * @see java.lang.Exception#Exception(String)
     */
    public ComponentRelatedException(@NotNull Component<? extends JavaPlugin> component, String message) {
        super(message);

        this.component = component;
        this.componentName = component.getClass().getName();
    }

    /**
     * Constructs a new exception linked to specific component with detail message and cause.
     * @param component component which caused this extension to occur
     * @param message the detail message
     * @param cause the cause
     * @see java.lang.Exception#Exception(String, Throwable) 
     */
    public ComponentRelatedException(@NotNull Component<? extends JavaPlugin> component, String message, Throwable cause) {
        super(message, cause);

        this.component = component;
        this.componentName = component.getClass().getName();
    }

    /**
     * Returns a component which caused this exception to occur
     * @return component which caused this exception to occur
     */
    @Nullable
    public Component<? extends JavaPlugin> getComponent() {
        return component;
    }

    /**
     * Returns the class name of the component that caused this exception to occur
     * @return class name of the component which caused this exception to occur
     */
    @NotNull
    public String getComponentName() {
        return componentName;
    }
}
