package io.github.brawaru.componential;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Plugin's component registry which keeps track of various plugin's components, initialises them,
 * adds as listeners, ensures correct integrity and much more
 *
 * @param <P> Type of plugin that this registry holds components for
 */
public class ComponentRegistry<P extends JavaPlugin> {

  /** All currently registered components */
  private final List<Class<? extends Component<? extends JavaPlugin>>> componentsRegistry = new ArrayList<>();

  /** All currently active (constructed) components */
  private final Map<Class<? extends Component<? extends JavaPlugin>>, Component<? extends JavaPlugin>> activeComponents =
      new HashMap<>();

  /** Weak map that stores values of pending initialisation states per each component's class */
  private static final Map<Class<? extends Component<? extends JavaPlugin>>, Boolean> pendingInitialisationValues =
      new WeakHashMap<>();

  /** Weak map that stores values of pending de-initialisation states per each component */
  private static final Map<Component<? extends JavaPlugin>, Boolean> pendingDeinitialisationValues =
      new WeakHashMap<>();

  /**
   * All resolved dependencies of component classes to avoid searching for the same dependencies
   * over and over again
   */
  private final Map<Class<? extends Component<? extends JavaPlugin>>, List<Class<? extends Component<? extends JavaPlugin>>>>
      resolvedDependencies = new WeakHashMap<>();

  /** All resolved dependents of the component classes that were listed as dependencies */
  private final Map<Class<? extends Component<? extends JavaPlugin>>, List<WeakReference<Class<? extends Component<? extends JavaPlugin>>>>>
      resolvedDependents = new WeakHashMap<>();

  /**
   * All currently active components that implement {@link Reloadable} interface and can be reloaded
   * at any time plugin requests so
   */
  private final List<WeakReference<Component<? extends JavaPlugin>>> reloadableComponents = new ArrayList<>();

  /** Plugin to which all instances of components being bound */
  private P boundPlugin;

  /** Class of plugin */
  private final Class<P> pluginClass;

  /**
   * Constructs a new component registry for provided plugin class
   *
   * @param pluginClass plugin's class for which components are stored in this registry
   */
  public ComponentRegistry(Class<P> pluginClass) {
    this.pluginClass = pluginClass;
  }

  /**
   * Registers a component and if other plugins are initialised, initialises it too
   *
   * @param component component for registration and initialisation
   * @return this registry class for chained calls
   */
  public ComponentRegistry<P> registerComponent(Class<? extends Component<? extends JavaPlugin>> component) {
    if (hasComponentRegistered(component)) return this;

    componentsRegistry.add(component);

    if (boundPlugin != null) initializeComponent(component);

    return this;
  }

  /**
   * Checks whether the component has been registered already
   *
   * @param componentClass component to check for registration
   * @return whether the component is already registered or not
   */
  public boolean hasComponentRegistered(Class<? extends Component<? extends JavaPlugin>> componentClass) {
    return componentsRegistry.contains(componentClass);
  }

  /**
   * Unregisters a component and de-initialises it
   *
   * @param componentClass component to unregister and de-initialise
   */
  public void unregisterComponent(Class<? extends Component<? extends JavaPlugin>> componentClass) {
    Component<? extends JavaPlugin> activeComponent = lookupActiveComponent(componentClass);

    if (activeComponent != null) {
      deinitializeComponent(activeComponent, null);

      activeComponents.remove(componentClass);
    }

    componentsRegistry.remove(componentClass);
  }

  /**
   * Register a component class as a dependent of another component class
   *
   * @param dependency dependency for which {@code dependent} is being accounted
   * @param dependent dependent that depends on {@code dependency}
   */
  private void registerDependent(
      Class<? extends Component<? extends JavaPlugin>> dependency, Class<? extends Component<? extends JavaPlugin>> dependent) {
    List<WeakReference<Class<? extends Component<? extends JavaPlugin>>>> dependents =
        resolvedDependents.computeIfAbsent(dependency, k -> new ArrayList<>());

    for (WeakReference<Class<? extends Component<? extends JavaPlugin>>> ref : dependents) {
      if (ref.get() == dependent) return;
    }

    dependents.add(new WeakReference<>(dependent));
  }

  /**
   * Unregisters a component class as a dependent of another component class
   *
   * @param dependency dependency for which {@code dependent} is being unlisted
   * @param dependent dependent that previously was dependent on {@code dependency}
   */
  private void unregisterDependent(
      Class<? extends Component<? extends JavaPlugin>> dependency, Class<? extends Component<? extends JavaPlugin>> dependent) {
    List<WeakReference<Class<? extends Component<? extends JavaPlugin>>>> dependentsRefs =
        resolvedDependents.get(dependency);

    if (dependentsRefs != null) {
      for (WeakReference<Class<? extends Component<? extends JavaPlugin>>> dependentRef : ListUtils.copyOf(dependentsRefs)) {
        Class<? extends Component<? extends JavaPlugin>> linkedDependent = dependentRef.get();

        if (linkedDependent == null || linkedDependent == dependent) {
          dependentsRefs.remove(dependentRef);
        }
      }

      if (dependentsRefs.isEmpty()) resolvedDependents.remove(dependency);
    }
  }

  /**
   * Returns a new list of active component dependents. As internally component dependents are
   * stored using weak references, if any of the references is already cleared, it will not be
   * included in the list
   *
   * @param component component for which list of dependents must be returned
   * @return list of active dependents
   */
  private List<Class<? extends Component<? extends JavaPlugin>>> getActiveComponentDependents(
      Component<? extends JavaPlugin> component) {
    List<WeakReference<Class<? extends Component<? extends JavaPlugin>>>> dependentsRefs =
        resolvedDependents.get(component.getClass());

    ArrayList<Class<? extends Component<? extends JavaPlugin>>> activeDependents = new ArrayList<>();

    if (dependentsRefs != null && !dependentsRefs.isEmpty()) {
      for (WeakReference<Class<? extends Component<? extends JavaPlugin>>> dependentsRef : dependentsRefs) {
        Class<? extends Component<? extends JavaPlugin>> dependent = dependentsRef.get();

        if (dependent != null) activeDependents.add(dependent);
      }
    }

    return activeDependents;
  }

  /**
   * Registers and initialises dependencies of the component class
   *
   * @param componentClass component class dependencies of which to register
   */
  private void registerAndInitializeDependencies(Class<? extends Component<? extends JavaPlugin>> componentClass) {
    for (Class<? extends Component<? extends JavaPlugin>> componentDependency :
        getComponentDependencies(componentClass)) {
      registerDependent(componentDependency, componentClass);
      if (!hasComponentRegistered(componentDependency)) registerComponent(componentDependency);
      if (!hasComponentInitialized(componentDependency)) initializeComponent(componentDependency);
    }
  }

  /**
   * Finds compatible constructor in components class and instantiates the component, returning its
   * instance
   *
   * @param componentClass component class to instantiate
   * @return instance of the component
   */
  private Component<? extends JavaPlugin> instantiateComponentClass(
      Class<? extends Component<? extends JavaPlugin>> componentClass) {
    // We're expecting to find at least one constructor that either accepts plugin as an argument or
    // with
    // no arguments at all. If no such constructor is found an exception must be thrown as it is
    // impossible
    // to instantiate the component class with no valid constructor.

    Component<? extends JavaPlugin> instance;

    Constructor<?> pluginConstructor = null;
    Constructor<?> regularConstructor = null;

    for (Constructor<?> declaredConstructor : componentClass.getDeclaredConstructors()) {
      Parameter[] parameters = declaredConstructor.getParameters();

      if (parameters.length == 1) {
        Parameter parameter = parameters[0];
        Class<?> paramType = parameter.getType();

        if (paramType.isAssignableFrom(pluginClass)) {
          pluginConstructor = declaredConstructor;
          break;
        }
      }

      if (parameters.length == 0 && regularConstructor == null) {
        regularConstructor = declaredConstructor;
        // Do not exit the loop to look for possible constructor with plugin argument
      }
    }

    if (pluginConstructor == null && regularConstructor == null) {
      throw new IllegalArgumentException(
          String.format(
              "Component class %s cannot be instantiated because no compatible constructor has been found",
              componentClass.getCanonicalName()));
    }

    try {
      if (pluginConstructor != null) {
        pluginConstructor.setAccessible(true);
        instance = componentClass.cast(pluginConstructor.newInstance(boundPlugin));
      } else {
        regularConstructor.setAccessible(true);
        instance = componentClass.cast(regularConstructor.newInstance());
      }
    } catch (IllegalAccessException
        | InstantiationException
        | InvocationTargetException exception) {
      throw new IllegalArgumentException(
          String.format(
              "Component class %s cannot be instantiated because of underlying reflection exception",
              componentClass.getCanonicalName()),
          exception);
    }

    return instance;
  }

  /**
   * Sets plugin field of the component to the currently bound plugin
   *
   * @param componentClass component class which plugin field has to be set
   * @param instance instance of the plugin on which field is being modified
   * @throws IllegalAccessException When field cannot be modified due to access issues which
   *     suggests that class might has been moved in incorrect package
   * @throws NoSuchFieldException When field cannot be found which suggests that the component class
   *     has been modified
   */
  private void setPluginField(
      Class<? extends Component<? extends JavaPlugin>> componentClass, Component<? extends JavaPlugin> instance)
      throws IllegalAccessException, NoSuchFieldException {
    Field pluginField = componentClass.getSuperclass().getDeclaredField("plugin");

    pluginField.set(instance, boundPlugin);
  }

  /**
   * Instantiates the component class for currently bound plugin
   *
   * @param componentClass component class to instantiate
   * @throws IllegalArgumentException If component cannot be instantiated due to underlying
   *     exception
   */
  private void initializeComponent(Class<? extends Component<? extends JavaPlugin>> componentClass) {
    if (activeComponents.containsKey(componentClass)) {
      throw new IllegalStateException(
          String.format(
              "The component %s is already initialised!", componentClass.getCanonicalName()));
    }

    if (isPendingInitialization(componentClass)) {
      throw new IllegalStateException(
          String.format(
              "The component %s is already pending initialisation (circular dependency?)",
              componentClass.getCanonicalName()));
    }

    setPendingInitialization(componentClass, true);

    registerAndInitializeDependencies(componentClass);

    Component<? extends JavaPlugin> instance;

    try {
      instance = instantiateComponentClass(componentClass);

      setPluginField(componentClass, instance);

      if (instance instanceof Initializable) {
        ((Initializable) instance).init();
      }
    } catch (Exception e) {
      setPendingInitialization(componentClass, false);

      throw new IllegalArgumentException(
          String.format(
              "Construction failed for class %s due to exception",
              componentClass.getCanonicalName()),
          e);
    }

    activeComponents.put(componentClass, instance);

    if (instance instanceof Reloadable) {
      reloadableComponents.add(new WeakReference<>(instance));
    }

    if (instance instanceof Listener) {
      Bukkit.getPluginManager().registerEvents((Listener) instance, boundPlugin);
    }

    setPendingInitialization(componentClass, false);
  }

  /**
   * Instantiates a collection of component classes
   *
   * @param collection collection to instantiate
   */
  private void initializeComponentsSet(Collection<Class<? extends Component<? extends JavaPlugin>>> collection) {
    List<Class<? extends Component<? extends JavaPlugin>>> queue = ListUtils.copyOf(collection);

    for (Class<? extends Component<? extends JavaPlugin>> componentClass : queue) {
      if (hasComponentInitialized(componentClass)) continue;

      initializeComponent(componentClass);
    }
  }

  /**
   * Instantiates all registered component classes after binding to passed plugin
   *
   * @param plugin plugin for which components in this registry instantiate
   * @return function to de-instantiate all registered components on plugin unload
   */
  public DeinitializationCall initializeComponents(@NotNull P plugin) {
    if (boundPlugin != null) {
      throw new IllegalStateException(
          "Attempt to initialise components when they're already initialised");
    }

    boundPlugin = plugin;

    initializeComponentsSet(componentsRegistry);

    reloadComponents();

    return this::deinitializeComponents;
  }

  /**
   * Looks for already instantiated component based on its class in and returns casted copy of it
   *
   * @param componentClass component class for which instantiated copy is returned
   * @param <C> type of component class to accept as parameter and return
   * @return either casted component instance or {@code null} if no component is not instantiated
   */
  private <C extends Component<? extends JavaPlugin>> C lookupActiveComponent(Class<C> componentClass) {
    Component<? extends JavaPlugin> component = activeComponents.get(componentClass);

    return component == null ? null : componentClass.cast(component);
  }

  /**
   * Looks for already instantiated component based on provided class and returns it, otherwise
   * throws an exception
   *
   * @param componentClass component class which instance to return
   * @param <C> type of component class to accept as parameter and return
   * @return component instance
   * @throws IllegalArgumentException If provided component class was not registered or not
   *     instantiated yet
   */
  public <C extends Component<? extends JavaPlugin>> C getActiveComponent(Class<C> componentClass) {
    C component = lookupActiveComponent(componentClass);

    if (component == null) {
      throw new IllegalArgumentException(
          String.format(
              "Component %s has not been initialised", componentClass.getCanonicalName()));
    }

    return component;
  }

  /**
   * Whether the component has already been instantiated
   *
   * @param componentClass component class to look for
   * @return whether the component has already been instantiated
   */
  public boolean hasComponentInitialized(Class<? extends Component<? extends JavaPlugin>> componentClass) {
    return activeComponents.containsKey(componentClass);
  }

  /**
   * Returns the dependencies of component or resolves them if no dependencies cached
   *
   * @param componentClass component class which dependencies to resolve
   * @return dependencies of provided component class
   */
  private List<Class<? extends Component<? extends JavaPlugin>>> getComponentDependencies(
      @NotNull Class<? extends Component<? extends JavaPlugin>> componentClass) {
    List<Class<? extends Component<? extends JavaPlugin>>> dependencies = resolvedDependencies.get(componentClass);

    if (dependencies == null) {
      dependencies = new ArrayList<>();

      DependsOn[] dependsOns = componentClass.getAnnotationsByType(DependsOn.class);

      for (DependsOn dependsOn : dependsOns) {
        dependencies.add(dependsOn.value());
      }

      dependencies = Collections.unmodifiableList(dependencies);

      resolvedDependencies.put(componentClass, dependencies);
    }

    return dependencies;
  }

  /**
   * Gets dependencies of the provided component instance
   *
   * <p>Under the hood this method will simply get a class of the component and resolve dependencies
   * using {@link #getComponentDependencies(Class)}
   *
   * @param component component which dependencies to resolve
   * @return dependencies of the provided component
   */
  private List<Class<? extends Component<? extends JavaPlugin>>> getComponentDependencies(
      @NotNull Component<? extends JavaPlugin> component) {
    Class<? extends Component<? extends JavaPlugin>> componentClass = getComponentClass(component);

    return getComponentDependencies(componentClass);
  }

  /**
   * Returns component class casted, this is required because Java Class method does not take
   * generic types into account resulting in unchecked warnings when attempting to use that class.
   * This method does the same cast but is marked as unchecked intentionally
   *
   * @param component component which class to return
   * @return generic class object of the component
   */
  @SuppressWarnings("unchecked")
  private Class<? extends Component<? extends JavaPlugin>> getComponentClass(@NotNull Component<? extends JavaPlugin> component) {
    // component.getClass is not generic as it comes from standard Java class which does not
    // have type annotations, so it will always return Class<?>. We have type parameters
    // assigned to this method, thus pretty much safe and can suppress this warning.
    return (Class<? extends Component<? extends JavaPlugin>>) component.getClass();
  }

  /**
   * Returns whether the provided component class is set for pending initialisation
   *
   * @param componentClass component class for which pending initialisation state is returned
   * @return this component's pending initialisation state
   */
  private static boolean isPendingInitialization(Class<? extends Component<? extends JavaPlugin>> componentClass) {
    return pendingInitialisationValues.getOrDefault(componentClass, false);
  }

  /**
   * Sets whether the provided component class is pending for initialisation
   *
   * @param componentClass component class for which pending initialisation state is set
   * @param value this component's pending initialisation state
   */
  private static void setPendingInitialization(
      Class<? extends Component<? extends JavaPlugin>> componentClass, boolean value) {
    pendingInitialisationValues.put(componentClass, value);
  }

  /**
   * Returns whether the provided component is set for pending de-initialisation
   *
   * @param component component for which pending de-initialisation state is returned
   * @return this component's pending de-initialisation state
   */
  private static boolean isPendingDeinitialization(Component<? extends JavaPlugin> component) {
    return pendingDeinitialisationValues.getOrDefault(component, false);
  }

  /**
   * Sets whether the provided component is pending for de-initialisation
   *
   * @param component component for which pending de-initialisation state is set
   * @param value this component's pending de-initialisation state
   */
  private static void setPendingDeinitialization(Component<? extends JavaPlugin> component, boolean value) {
    pendingDeinitialisationValues.put(component, value);
  }

  /**
   * Reloads all the dependencies of the component
   *
   * <p>To be used before reloading components itself
   *
   * @param reloadable component, which dependencies to reload
   * @param reloaded list of all dependencies reloaded
   */
  private void reloadDependencies(
      Component<? extends JavaPlugin> reloadable, Set<Component<? extends JavaPlugin>> reloaded) {
    if (!(reloadable instanceof Reloadable)) return;

    List<Class<? extends Component<? extends JavaPlugin>>> componentDependencies = getComponentDependencies(reloadable);

    for (Class<? extends Component<? extends JavaPlugin>> componentDependency : componentDependencies) {
      Component<? extends JavaPlugin> dependentComponent = getActiveComponent(componentDependency);

      if (!(dependentComponent instanceof Reloadable)) continue;

      Reloadable reloadableComponent = (Reloadable) dependentComponent;

      if (reloaded.contains(dependentComponent)) continue;

      reloadableComponent.reload();

      reloaded.add(dependentComponent);
    }
  }

  /**
   * Reloads provided collection of components. Any components that do not implement Reloadable
   * components will be skipped
   *
   * @param collection collection of the components to reload
   */
  private void reloadComponentsSet(Collection<WeakReference<Component<? extends JavaPlugin>>> collection) {
    final List<WeakReference<Component<? extends JavaPlugin>>> reloadableSet = ListUtils.copyOf(collection);

    Set<Component<? extends JavaPlugin>> reloaded = new HashSet<>();

    for (WeakReference<Component<? extends JavaPlugin>> reloadableRef : reloadableSet) {
      Component<? extends JavaPlugin> reloadable = reloadableRef.get();

      if (reloadable != null) {
        reloadDependencies(reloadable, reloaded);

        if (!reloaded.contains(reloadable)) {
          if (!(reloadable instanceof Reloadable)) continue;

          ((Reloadable) reloadable).reload();

          reloaded.add(reloadable);

          continue;
        }
      }

      reloadableComponents.remove(reloadableRef);
    }
  }

  /** Reloads all the registered components that implement Reloadable interface */
  public void reloadComponents() {
    reloadComponentsSet(reloadableComponents);
  }

  /**
   * Deinitialises component's dependents
   *
   * <p>To be used before unloading the component itself
   *
   * @param component component which dependents must be unloaded
   * @param deinitialisationQueue collection of components that are queued for reload
   * @throws IllegalStateException If {@code deinitialisationQueue} is {@code null} or does not
   *     contain one of the dependencies the operation is aborted with this exception
   */
  private void deinitializeComponentDependencies(
      @NotNull Component<? extends JavaPlugin> component,
      @Nullable Collection<Component<? extends JavaPlugin>> deinitialisationQueue) {
    List<Class<? extends Component<? extends JavaPlugin>>> componentDependents =
        getActiveComponentDependents(component);

    Class<? extends Component<? extends JavaPlugin>> componentClass = getComponentClass(component);

    if (componentDependents.isEmpty()) return;

    if (deinitialisationQueue == null) {
      throw new IllegalStateException(
          String.format(
              "The component %s cannot be de-initialised because its dependents are still active",
              component.getClass().getName()));
    }

    // Check up that this 'transaction' will be safe to do before committing any action
    for (Class<? extends Component<? extends JavaPlugin>> componentDependent : componentDependents) {
      Component<? extends JavaPlugin> activeDependent = activeComponents.get(componentDependent);

      if (activeDependent == null || isPendingDeinitialization(activeDependent)) continue;

      if (!deinitialisationQueue.contains(activeDependent)) {
        throw new IllegalStateException(
            String.format(
                "The component %s cannot be de-initialised because its dependency %s is still active",
                component.getClass().getName(), activeDependent.getClass().getName()));
      }
    }

    for (Class<? extends Component<? extends JavaPlugin>> componentDependent : componentDependents) {
      Component<? extends JavaPlugin> activeDependent = activeComponents.get(componentDependent);

      if (activeDependent != null && !isPendingDeinitialization(activeDependent)) {
        deinitializeComponent(activeDependent, deinitialisationQueue);

        unregisterDependent(componentClass, componentDependent);
      }
    }
  }

  /**
   * De-initialises individual component in graceful manner
   *
   * @param component component to unload
   * @param deinitialisationQueue collection of components that are queued for unload
   */
  private void deinitializeComponent(
      @NotNull Component<? extends JavaPlugin> component,
      @Nullable Collection<Component<? extends JavaPlugin>> deinitialisationQueue) {
    if (isPendingDeinitialization(component)) {
      throw new IllegalStateException(
          "The component %s is already pending de-initialisation (circular dependency?)");
    }

    setPendingDeinitialization(component, true);

    try {
      deinitializeComponentDependencies(component, deinitialisationQueue);

      if (component instanceof Unloadable) {
        ((Unloadable) component).unload();
      }

      if (component instanceof Listener) {
        HandlerList.unregisterAll((Listener) component);
      }
    } finally {
      setPendingDeinitialization(component, false);
    }
  }

  /**
   * De-initialises collection of the components
   *
   * @param collection collection of the components to de-initialise
   * @param exceptionHandler an interface that would handle the exceptions during the
   *     de-initialisation
   * @throws ComponentRelatedException If de-initialisation of the specific component has failed and
   *     was unhandled
   */
  private void deinitializeComponentsSet(
      Collection<@NotNull Component<? extends JavaPlugin>> collection,
      @Nullable ExceptionHandler<ComponentRelatedException> exceptionHandler)
      throws ComponentRelatedException {
    List<Component<? extends JavaPlugin>> queue = ListUtils.copyOf(collection);

    for (Component<? extends JavaPlugin> component : queue) {
      Class<? extends Component<? extends JavaPlugin>> componentClass = getComponentClass(component);

      try {
        deinitializeComponent(component, queue);
      } catch (Exception exception) {
        ComponentRelatedException futureException =
            new ComponentRelatedException(
                component,
                String.format(
                    "An exception has occurred while de-initialising component %s",
                    component.getClass().getName()),
                exception);

        if (exceptionHandler == null) throw futureException;

        if (exceptionHandler.shouldContinue(futureException)) {
          continue;
        } else {
          break;
        }
      }

      Component<? extends JavaPlugin> activeComponent = activeComponents.get(componentClass);

      if (activeComponent == component) {
        activeComponents.remove(componentClass);
      }
    }
  }

  /**
   * De-initialises all the components previously registered and activated
   *
   * @throws ComponentRelatedException If any of the components has failed unloading
   */
  private void deinitializeComponents() throws ComponentRelatedException {
    if (boundPlugin == null) {
      throw new IllegalStateException(
          "Cannot de-initialise components when they weren't initialised yet");
    }

    deinitializeComponentsSet(
        activeComponents.values(),
        exception -> {
          boundPlugin
              .getLogger()
              .warning(
                  () ->
                      String.format(
                          "[ComponentRegistry] Failed to de-initialise component %s, an exception has been thrown%n%s",
                          exception.getComponentName(),
                          ExceptionUtils.getFullStackTrace(exception)));

          return true;
        });

    boundPlugin = null;
  }
}
