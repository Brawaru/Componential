# Componential

> Small Bukkit library to split plugin's code more effectively. Poor man's dependency injection.

### How does it work?

Componential works based on Bukkit's plugin singleton conception — for one plugin only one instance of main class is 
created. With componential, you make a static field with its registry that you add some classes to, when plugin gets 
enabled, you request to instantiate all the sub-classes of plugin's logic. A few annotations help you make sure that 
the order of instantiation is the right one.

Here's the example implementation of your ExamplePlugin class:

<details>
<summary>ExamplePlugin implementation</summary>

```java
package io.github.brawaru.exampleplugin;

import io.github.brawaru.componential.ComponentRegistry;
import io.github.brawaru.componential.ComponentRelatedException;
import io.github.brawaru.componential.DeinitializationCall;
import io.github.brawaru.exampleplugin.configs.ConfigLoader;
import io.github.brawaru.exampleplugin.interactive.Commands;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {

    
    private static final ComponentRegistry<ExamplePlugin> componentRegistry =
            new ComponentRegistry<>(ExamplePlugin.class)
                    .registerComponent(ConfigLoader.class)
                    .registerComponent(Commands.class);

    private DeinitializationCall deinitialisationHandler = null;

    public static ComponentRegistry<ExamplePlugin> getComponentRegistry() {
        return componentRegistry;
    }

    @Override
    public void onEnable() {
        deinitialisationHandler = componentRegistry.initializeComponents(this);
    }

    public void reload() {
        componentRegistry.reloadComponents();
    }

    @Override
    public void onDisable() {
        if (deinitialisationHandler != null) {
            try {
                deinitialisationHandler.deinitialize();
            } catch (ComponentRelatedException exception) {
                getLogger().severe(String.format("One of the components has failed to unload completely due to exception%n%s",
                        ExceptionUtils.getFullStackTrace(exception)));
            }
        }
    }
}
```

</details>

In summary, all we have done so far:
- We have created a static field for registry instance, registering two components: `ConfigLoader` and `Commands` by 
  using their classes references.
- After that we statically exposed our registry, allowing components to interact with each other by accessing 
  registry.
- In `onEnable` we initialise all the components we previously registered. During the initialisation additional 
  components might be automatically registered and instantiated if one of the components defines them as their 
  dependency.
  - Notice that initialisation method returns us a lambda that we should store and use to unload the components later, 
    this is a safety control, so the components themselves cannot unload whole registry.
- We created example `reload` method, it wasn't required, as other components can simply call `reloadComponents` 
  method manually.
- In `onDisable` we check whether we have deinitialisation handler stored, and if we do, call it to deinitialise the 
  components. We do that check because if exception occurred during initialisation, no handler will be returned, and 
  we'll cause NPE. Also, gracefully handling the exceptions that arise during unload. You might de-reference handler 
  to be sure, but you'll need to instantiate it again during enabling of plugin.
  
And this is how components look:

<details>
<summary>Example of Commands class</summary>

```java
package io.github.brawaru.exampleplugin.interactive;

import io.github.brawaru.componential.Component;
import io.github.brawaru.componential.DependsOn;
import io.github.brawaru.exampleplugin.ExamplePlugin;
import io.github.brawaru.exampleplugin.configs.ConfigLoader;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;

@DependsOn(ConfigLoader.class)
public class Commands extends Component<ExamplePlugin> {
    protected Commands(ExamplePlugin plugin) {
        PluginCommand exampleCommand = plugin.getCommand("examplecommand");
        
        if (exampleCommand == null) {
            throw new IllegalStateException("Example command is null");
        }
        
        exampleCommand.setExecutor(this::handleExampleCommand);
    }
    
    private void handleExampleCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("Hello from example command!");
        sender.sendMessage(ConfigLoader.instance().getMOTD());
    }
    
    private static void instance() {
        return ExamplePlugin.getComponentRegistry().getActiveComponent(Commands.class);
    }
}
```

</details>

Here not much extraordinary happens:
- You start by creating a class that extends `Component<[pluginClass]>`
- By using `@DependsOn(ConfigLoader.class)` annotation on that class you declare that `ConfigLoader` must be 
  instantiated before this component as it is depends on it (duh).
- You have two choices after: you can rely on constructor detection and create a constructor that accepts only 
  `ExamplePlugin` parameter, or you can implement `Initializeable` class with `init()` method, but then you'll have 
  to check whether the class was initialised already (simple boolean should do the trick).
- Notice how we use static `instance()` method of `ConfigLoader`, we have exactly the same implementation for 
  `Commands` class below. Basically you can create such shortcut methods, they look very nice.
  
That's basically it. We do not define any `getSomething()` getters on our plugin, do not instantiate 
anything manually, it's all done by using annotations and simple registry. That comes in handy as your plugin grows!

### What are the pros?

- You don't pollute your plugin's main class with many many many `getComponent()` methods. The only thing you do is 
  register components and call for components initialisation.
- You don't instantiate any of components manually and instead allow Componential to deal with it, in correct order.
- It's much easier to follow in the code what your intentions are and how components interact with each other.

### Why not Componential?

There are several reasons why you wouldn't want to use Componential:

- You're working with a somewhat small codebase, where adding Componential won't give you any benefits.
- Componential uses and depends on reflection too much. It's not bad, but might not suit you.
- You probably need to shade Componential dependency to avoid conflicts with other plugins that use it.
- You are already using more advanced dependency injection solution.

### Future plans

- Instead of relying on static shortcut methods it might be a better approach to automatically inject correct 
  component classes into annotated fields (might also use that to avoid declaration of dependencies?). This, however,
  might make usage of constructors dangerous.
  
### Support

If you liked Componential's idea, consider contributing to it. Or you might support the author with by [giving a few 
pennies here](https://yoomoney.ru/to/410014746904198) :)