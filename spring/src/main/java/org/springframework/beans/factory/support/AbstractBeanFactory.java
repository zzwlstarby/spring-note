/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.support;

import java.beans.PropertyEditor;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Abstract base class for {@link org.springframework.beans.factory.BeanFactory}
 * implementations, providing the full capabilities of the
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} SPI.
 * Does <i>not</i> assume a listable bean factory: can therefore also be used
 * as base class for bean factory implementations which obtain bean definitions
 * from some backend resource (where bean definition access is an expensive operation).
 * <p>
 * {@link org.springframework.beans.factory.BeanFactory}接口的基础抽象实现,通过SPI提供
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}接口的完整功能
 * 不是一个可列表的bean工厂: 因此可以作为bean工厂实现的基类,从某些后端资源(当其中beanDefinition访问
 * 是一个昂贵的操作)获取beanDefinition
 * <p>
 * <p>
 * <p>This class provides a singleton cache (through its base class
 * {@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * singleton/prototype determination, {@link org.springframework.beans.factory.FactoryBean}
 * handling, aliases, bean definition merging for child bean definitions,
 * and bean destruction ({@link org.springframework.beans.factory.DisposableBean}
 * interface, custom destroy methods). Furthermore, it can manage a bean factory
 * hierarchy (delegating to the parent in case of an unknown bean), through implementing
 * the {@link org.springframework.beans.factory.HierarchicalBeanFactory} interface.
 * <p>
 * 这个类提供了一个单例的缓存(通过基类{@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * 确定单例和原型模式,{@link org.springframework.beans.factory.FactoryBean}的处理,别名,子类beanDefinition
 * 的合并和({@link org.springframework.beans.factory.DisposableBean}的销毁).
 * <p>
 * 此外他还通过实现{@link org.springframework.beans.factory.HierarchicalBeanFactory}接口来管理有父类的bean工厂
 * (如果有在当前工厂中查询不到的bean,委托给父工厂处理)
 * <p>
 * <p>
 * <p>The main template methods to be implemented by subclasses are
 * {@link #getBeanDefinition} and {@link #createBean}, retrieving a bean definition
 * for a given bean name and creating a bean instance for a given bean definition,
 * respectively. Default implementations of those operations can be found in
 * {@link DefaultListableBeanFactory} and {@link AbstractAutowireCapableBeanFactory}.
 * <p>
 * 子类需要实现的主要模板方法有{@link #getBeanDefinition} 和 {@link #createBean},分别给指定bean名称检索出beanDefinition和
 * beanDefinition创建bean的实例.
 * 这些操作的默认实现可以在{@link DefaultListableBeanFactory} 和 {@link AbstractAutowireCapableBeanFactory}中找到
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @see #getBeanDefinition
 * @see #createBean
 * @see AbstractAutowireCapableBeanFactory#createBean
 * @see DefaultListableBeanFactory#getBeanDefinition
 * @since 15 April 2001
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

    /**
     * Parent bean factory, for bean inheritance support
     */
    // 父bean工厂,用于bean继承支持
    private BeanFactory parentBeanFactory;

    /**
     * ClassLoader to resolve bean class names with, if necessary
     */
    // 如果需要的话,使用类加载器来解析bean类名
    private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

    /**
     * ClassLoader to temporarily resolve bean class names with, if necessary
     */
    // 如果需要的话,使用临时的类加载器来解析bean类名
    private ClassLoader tempClassLoader;

    /**
     * Whether to cache bean metadata or rather reobtain it for every access
     */
    // 是缓存bean的元数据还是每次访问重新获取数据
    private boolean cacheBeanMetadata = true;

    /**
     * Resolution strategy for expressions in bean definition values
     */
    // bean定义值中表达式的解析策略
    private BeanExpressionResolver beanExpressionResolver;

    /**
     * Spring 3.0 ConversionService to use instead of PropertyEditors
     */
    // 使用Spring 3.0 ConversionService代替PropertyEditors
    private ConversionService conversionService;

    /**
     * Custom PropertyEditorRegistrars to apply to the beans of this factory
     */
    // 在此工厂的bean中使用自定义PropertyEditorRegistrars
    private final Set<PropertyEditorRegistrar> propertyEditorRegistrars =
            new LinkedHashSet<PropertyEditorRegistrar>(4);

    /**
     * A custom TypeConverter to use, overriding the default PropertyEditor mechanism
     */
    // 要使用的自定义类型转换程序,覆盖默认的PropertyEditor机制
    private TypeConverter typeConverter;

    /**
     * Custom PropertyEditors to apply to the beans of this factory
     */
    // 在此工厂的bean中使用自定义PropertyEditors
    private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors =
            new HashMap<Class<?>, Class<? extends PropertyEditor>>(4);

    /**
     * String resolvers to apply e.g. to annotation attribute values
     */
    // 应用字符串解析器,例如注解属性值
    private final List<StringValueResolver> embeddedValueResolvers = new LinkedList<StringValueResolver>();

    /**
     * BeanPostProcessors to apply in createBean
     */
    // 在创建bean时的bean后置处理器
    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<BeanPostProcessor>();

    /**
     * Indicates whether any Instantiation AwareBeanPostProcessors have been registered
     */
    // 标志是否注册了任何实例化AwareBeanPostProcessors
    private boolean hasInstantiationAwareBeanPostProcessors;

    /**
     * Indicates whether any DestructionAwareBeanPostProcessors have been registered
     */
    // 标志是否注册了任何销毁的AwareBeanPostProcessors
    private boolean hasDestructionAwareBeanPostProcessors;

    /**
     * Map from scope identifier String to corresponding Scope
     */
    // 范围标识符字符串和相应的范围的映射(用来控制bean的使用范围,如在web环境下)
    private final Map<String, Scope> scopes = new HashMap<String, Scope>(8);

    /**
     * Security context used when running with a SecurityManager
     */
    // 使用SecurityManager,运行时使用的安全的上下文
    private SecurityContextProvider securityContextProvider;

    /**
     * Map from bean name to merged RootBeanDefinition
     */
    // bean名称和需要合并的RootBeanDefinition映射
    private final Map<String, RootBeanDefinition> mergedBeanDefinitions =
            new ConcurrentHashMap<String, RootBeanDefinition>(64);

    /**
     * Names of beans that have already been created at least once
     * (using a ConcurrentHashMap as a Set)
     * <p>
     * 已经至少创建一次的bean的名称(使用ConcurrentHashMap替代SET的功能)
     */
    private final Map<String, Boolean> alreadyCreated = new ConcurrentHashMap<String, Boolean>(64);

    /**
     * Names of beans that are currently in creation
     */
    // 正在创建的bean名称集合
    private final ThreadLocal<Object> prototypesCurrentlyInCreation =
            new NamedThreadLocal<Object>("Prototype beans currently in creation");


    /**
     * Create a new AbstractBeanFactory.
     */
    public AbstractBeanFactory() {
    }

    /**
     * Create a new AbstractBeanFactory with the given parent.
     * 使用给定的父工厂创建AbstractBeanFactory
     *
     * @param parentBeanFactory parent bean factory, or {@code null} if none
     * @see #getBean
     */
    public AbstractBeanFactory(BeanFactory parentBeanFactory) {
        this.parentBeanFactory = parentBeanFactory;
    }


    //---------------------------------------------------------------------
    // Implementation of BeanFactory interface
    //---------------------------------------------------------------------

    public Object getBean(String name) throws BeansException {
        return doGetBean(name, null, null, false);
    }

    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        return doGetBean(name, requiredType, null, false);
    }

    public Object getBean(String name, Object... args) throws BeansException {
        return doGetBean(name, null, args, false);
    }

    /**
     * Return an instance, which may be shared or independent, of the specified bean.
     * 返回指定bean的一个实例,该实例可以是共享的,也可以是独立的
     *
     * @param name         the name of the bean to retrieve
     * @param requiredType the required type of the bean to retrieve
     * @param args         arguments to use if creating a prototype using explicit arguments to a
     *                     static factory method. It is invalid to use a non-null args value in any other case.
     *                     使用静态工厂方法显示参数创建原型时使用的参数,其他情形下使用非空参数都是无效的
     * @return an instance of the bean
     * @throws BeansException if the bean could not be created
     */
    public <T> T getBean(String name, Class<T> requiredType, Object... args) throws BeansException {
        return doGetBean(name, requiredType, args, false);
    }

    /**
     * Return an instance, which may be shared or independent, of the specified bean.
     * 返回指定bean的一个实例,该实例可以是共享的,也可以是独立的
     *
     * @param name          the name of the bean to retrieve
     * @param requiredType  the required type of the bean to retrieve
     *                      bean的类型
     * @param args          arguments to use if creating a prototype using explicit arguments to a
     *                      static factory method. It is invalid to use a non-null args value in any other case.
     *                      使用静态工厂方法显示参数创建原型时使用的参数,其他情形下使用非空参数都是无效的
     * @param typeCheckOnly whether the instance is obtained for a type check,
     *                      not for actual use
     *                      获取实例是否用于类型检查,而不是实际使用
     * @return an instance of the bean
     * @throws BeansException if the bean could not be created
     */
    @SuppressWarnings("unchecked")
    protected <T> T doGetBean(
            final String name, final Class<T> requiredType, final Object[] args, boolean typeCheckOnly)
            throws BeansException {

        // 解析bean的名称
        final String beanName = transformedBeanName(name);
        Object bean;

        // Eagerly check singleton cache for manually registered singletons.
        // 提前检查单例缓存是否有手动注册的单例。
        Object sharedInstance = getSingleton(beanName);
        // 如果缓存中存在并且args为空
        if (sharedInstance != null && args == null) {
            if (logger.isDebugEnabled()) {
                if (isSingletonCurrentlyInCreation(beanName)) {
                    logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
                            "' that is not fully initialized yet - a consequence of a circular reference");
                } else {
                    logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
                }
            }
            // 返回bean的实例, sharedInstance可能为FactoryBean,所有不能直接返回
            bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
        } else {
            // Fail if we're already creating this bean instance:
            // We're assumably within a circular reference.
            // 如果我们已经创建了这个bean实例，就会失败:我们假设是在循环引用中
            if (isPrototypeCurrentlyInCreation(beanName)) {
                // 原型模式抛出异常
                throw new BeanCurrentlyInCreationException(beanName);
            }

            // Check if bean definition exists in this factory.
            // 校验beanDefinition是否在该工厂中
            BeanFactory parentBeanFactory = getParentBeanFactory();
            // 如果父工厂不为空,并且当前工厂不存在该bean
            if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
                // Not found -> check parent.
                String nameToLookup = originalBeanName(name);
                if (args != null) {
                    // Delegation to parent with explicit args.
                    // 委托给父工厂查找
                    return (T) parentBeanFactory.getBean(nameToLookup, args);
                } else {
                    // No args -> delegate to standard getBean method.
                    // 没有args -> 委托给标准的getBean方法
                    return parentBeanFactory.getBean(nameToLookup, requiredType);
                }
            }

            // 如果不需要类型检查,标记当前bean在创建中
            if (!typeCheckOnly) {
                markBeanAsCreated(beanName);
            }

            try {
                // 获取该bean的RootBeanDefinition
                final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                checkMergedBeanDefinition(mbd, beanName, args);

                // Guarantee initialization of beans that the current bean depends on.
                // 确保初始化当前bean所依赖的bean
                String[] dependsOn = mbd.getDependsOn();
                // 如果依赖的bean不为空
                if (dependsOn != null) {
                    // 需要先创建依赖的bean
                    for (String dependsOnBean : dependsOn) {
                        getBean(dependsOnBean);
                        registerDependentBean(dependsOnBean, beanName);
                    }
                }

                // Create bean instance.
                // 创建bean的实例
                // 如果为单例
                if (mbd.isSingleton()) {
                    // 委托给子类创建bean
                    sharedInstance = getSingleton(beanName, new ObjectFactory<Object>() {
                        public Object getObject() throws BeansException {
                            try {
                                return createBean(beanName, mbd, args);
                            } catch (BeansException ex) {
                                // Explicitly remove instance from singleton cache: It might have been put there
                                // eagerly by the creation process, to allow for circular reference resolution.
                                // Also remove any beans that received a temporary reference to the bean.
                                destroySingleton(beanName);
                                throw ex;
                            }
                        }
                    });
                    // 由于创建出来的bean可能是factoryBean所以需要再次判断处理
                    bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
                }

                // 如果是原型模式
                else if (mbd.isPrototype()) {
                    // It's a prototype -> create a new instance.
                    Object prototypeInstance = null;
                    try {
                        // bean的前置处理
                        beforePrototypeCreation(beanName);
                        // 创建bean
                        prototypeInstance = createBean(beanName, mbd, args);
                    } finally {
                        // 后置处理
                        afterPrototypeCreation(beanName);
                    }
                    // 由于创建出来的bean可能是factoryBean所以需要再次判断处理
                    bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
                }

                // 其他情况,需要判断Scope的范围
                else {
                    String scopeName = mbd.getScope();
                    final Scope scope = this.scopes.get(scopeName);
                    // 如果scope为空,那么就不能创建
                    if (scope == null) {
                        throw new IllegalStateException("No Scope registered for scope '" + scopeName + "'");
                    }
                    try {
                        // 否则在该scope下创建实例
                        Object scopedInstance = scope.get(beanName, new ObjectFactory<Object>() {
                            public Object getObject() throws BeansException {
                                beforePrototypeCreation(beanName);
                                try {
                                    // 委托给子类实现
                                    return createBean(beanName, mbd, args);
                                } finally {
                                    // 后置处理
                                    afterPrototypeCreation(beanName);
                                }
                            }
                        });
                        // 由于创建出来的bean可能是factoryBean所以需要再次判断处理
                        bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                    } catch (IllegalStateException ex) {
                        throw new BeanCreationException(beanName,
                                "Scope '" + scopeName + "' is not active for the current thread; " +
                                        "consider defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                                ex);
                    }
                }
            } catch (BeansException ex) {
                // 如果异常,从当前正在创建的bean中移除
                cleanupAfterBeanCreationFailure(beanName);
                throw ex;
            }
        }

        // Check if required type matches the type of the actual bean instance.
        // 检查所需的类型是否与实际bean实例的类型匹配
        if (requiredType != null && bean != null && !requiredType.isAssignableFrom(bean.getClass())) {
            try {
                // 类型不匹配,需要类型转换器转换
                return getTypeConverter().convertIfNecessary(bean, requiredType);
            } catch (TypeMismatchException ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to convert bean '" + name + "' to required type [" +
                            ClassUtils.getQualifiedName(requiredType) + "]", ex);
                }
                throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            }
        }
        // 返回bean
        return (T) bean;
    }

    // 检查是否包含指定名称的bean
    public boolean containsBean(String name) {
        String beanName = transformedBeanName(name);
        // 如果包含bean的实例或者包含bean的BeanDefinition
        if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
            // 如果bean名称没有FACTORY_BEAN_PREFIX直接返回存在,否则判断是否存在FactoryBean实例,存在表示该bean存在
            return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
        }
        // Not found -> check parent.
        // 如果当前工厂查询不到,查询父工厂
        BeanFactory parentBeanFactory = getParentBeanFactory();
        return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
    }

    // 判断指定名称是否是单例
    public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
        String beanName = transformedBeanName(name);

        Object beanInstance = getSingleton(beanName, false);
        if (beanInstance != null) {
            // 如果bean实例不为空
            if (beanInstance instanceof FactoryBean) {
                // 如果bean名称没有FACTORY_BEAN_PREFIX直接返回存在,否则判断FactoryBean.isSingleton()
                return (BeanFactoryUtils.isFactoryDereference(name) || ((FactoryBean<?>) beanInstance).isSingleton());
            } else {
                // 如果不为FactoryBean实例则返回true
                return !BeanFactoryUtils.isFactoryDereference(name);
            }
        }
        // 如果包含bean的单例实例直接返回
        else if (containsSingleton(beanName)) {
            return true;
        } else {
            // No singleton instance found -> check bean definition.
            // 如果当前工厂中没有单例.检查beanDefinition
            BeanFactory parentBeanFactory = getParentBeanFactory();
            if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
                // No bean definition found in this factory -> delegate to parent.
                // 如果当前工厂找不到beanDefinition,那么从父工厂中查询
                return parentBeanFactory.isSingleton(originalBeanName(name));
            }

            RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

            // In case of FactoryBean, return singleton status of created object if not a dereference.
            // 对于FactoryBean,如果没有取消引用,则返回已创建对象的单例状态。
            if (mbd.isSingleton()) {
                if (isFactoryBean(beanName, mbd)) {
                    if (BeanFactoryUtils.isFactoryDereference(name)) {
                        return true;
                    }
                    FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
                    return factoryBean.isSingleton();
                } else {
                    return !BeanFactoryUtils.isFactoryDereference(name);
                }
            } else {
                return false;
            }
        }
    }

    // 判断是否原型模式
    public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
        String beanName = transformedBeanName(name);

        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // No bean definition found in this factory -> delegate to parent.
            return parentBeanFactory.isPrototype(originalBeanName(name));
        }

        RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
        if (mbd.isPrototype()) {
            // In case of FactoryBean, return singleton status of created object if not a dereference.
            return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
        } else {
            // Singleton or scoped - not a prototype.
            // However, FactoryBean may still produce a prototype object...
            if (BeanFactoryUtils.isFactoryDereference(name)) {
                return false;
            }
            if (isFactoryBean(beanName, mbd)) {
                final FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
                if (System.getSecurityManager() != null) {
                    return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                        public Boolean run() {
                            return ((factoryBean instanceof SmartFactoryBean && ((SmartFactoryBean<?>) factoryBean).isPrototype()) ||
                                    !factoryBean.isSingleton());
                        }
                    }, getAccessControlContext());
                } else {
                    return ((factoryBean instanceof SmartFactoryBean && ((SmartFactoryBean<?>) factoryBean).isPrototype()) ||
                            !factoryBean.isSingleton());
                }
            } else {
                return false;
            }
        }
    }

    public boolean isTypeMatch(String name, Class<?> targetType) throws NoSuchBeanDefinitionException {
        String beanName = transformedBeanName(name);
        Class<?> typeToMatch = (targetType != null ? targetType : Object.class);

        // Check manually registered singletons.
        Object beanInstance = getSingleton(beanName, false);
        if (beanInstance != null) {
            if (beanInstance instanceof FactoryBean) {
                if (!BeanFactoryUtils.isFactoryDereference(name)) {
                    Class<?> type = getTypeForFactoryBean((FactoryBean<?>) beanInstance);
                    return (type != null && ClassUtils.isAssignable(typeToMatch, type));
                } else {
                    return ClassUtils.isAssignableValue(typeToMatch, beanInstance);
                }
            } else {
                return !BeanFactoryUtils.isFactoryDereference(name) &&
                        ClassUtils.isAssignableValue(typeToMatch, beanInstance);
            }
        } else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
            // null instance registered
            return false;
        } else {
            // No singleton instance found -> check bean definition.
            BeanFactory parentBeanFactory = getParentBeanFactory();
            if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
                // No bean definition found in this factory -> delegate to parent.
                return parentBeanFactory.isTypeMatch(originalBeanName(name), targetType);
            }

            // Retrieve corresponding bean definition.
            RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

            Class[] typesToMatch = (FactoryBean.class.equals(typeToMatch) ?
                    new Class[]{typeToMatch} : new Class[]{FactoryBean.class, typeToMatch});

            // Check decorated bean definition, if any: We assume it'll be easier
            // to determine the decorated bean's type than the proxy's type.
            BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
            if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
                RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
                Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
                if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
                    return typeToMatch.isAssignableFrom(targetClass);
                }
            }

            Class<?> beanType = predictBeanType(beanName, mbd, typesToMatch);
            if (beanType == null) {
                return false;
            }

            // Check bean class whether we're dealing with a FactoryBean.
            if (FactoryBean.class.isAssignableFrom(beanType)) {
                if (!BeanFactoryUtils.isFactoryDereference(name)) {
                    // If it's a FactoryBean, we want to look at what it creates, not the factory class.
                    beanType = getTypeForFactoryBean(beanName, mbd);
                    if (beanType == null) {
                        return false;
                    }
                }
            } else if (BeanFactoryUtils.isFactoryDereference(name)) {
                // Special case: A SmartInstantiationAwareBeanPostProcessor returned a non-FactoryBean
                // type but we nevertheless are being asked to dereference a FactoryBean...
                // Let's check the original bean class and proceed with it if it is a FactoryBean.
                beanType = predictBeanType(beanName, mbd, FactoryBean.class);
                if (beanType == null || !FactoryBean.class.isAssignableFrom(beanType)) {
                    return false;
                }
            }

            return typeToMatch.isAssignableFrom(beanType);
        }
    }

    public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
        String beanName = transformedBeanName(name);

        // Check manually registered singletons.
        Object beanInstance = getSingleton(beanName, false);
        if (beanInstance != null) {
            if (beanInstance instanceof FactoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
                return getTypeForFactoryBean((FactoryBean<?>) beanInstance);
            } else {
                return beanInstance.getClass();
            }
        } else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
            // null instance registered
            return null;
        } else {
            // No singleton instance found -> check bean definition.
            BeanFactory parentBeanFactory = getParentBeanFactory();
            if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
                // No bean definition found in this factory -> delegate to parent.
                return parentBeanFactory.getType(originalBeanName(name));
            }

            RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

            // Check decorated bean definition, if any: We assume it'll be easier
            // to determine the decorated bean's type than the proxy's type.
            BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
            if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
                RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
                Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
                if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
                    return targetClass;
                }
            }

            Class<?> beanClass = predictBeanType(beanName, mbd);

            // Check bean class whether we're dealing with a FactoryBean.
            if (beanClass != null && FactoryBean.class.isAssignableFrom(beanClass)) {
                if (!BeanFactoryUtils.isFactoryDereference(name)) {
                    // If it's a FactoryBean, we want to look at what it creates, not at the factory class.
                    return getTypeForFactoryBean(beanName, mbd);
                } else {
                    return beanClass;
                }
            } else {
                return (!BeanFactoryUtils.isFactoryDereference(name) ? beanClass : null);
            }
        }
    }

    @Override
    public String[] getAliases(String name) {
        String beanName = transformedBeanName(name);
        List<String> aliases = new ArrayList<String>();
        boolean factoryPrefix = name.startsWith(FACTORY_BEAN_PREFIX);
        String fullBeanName = beanName;
        if (factoryPrefix) {
            fullBeanName = FACTORY_BEAN_PREFIX + beanName;
        }
        if (!fullBeanName.equals(name)) {
            aliases.add(fullBeanName);
        }
        String[] retrievedAliases = super.getAliases(beanName);
        for (String retrievedAlias : retrievedAliases) {
            String alias = (factoryPrefix ? FACTORY_BEAN_PREFIX : "") + retrievedAlias;
            if (!alias.equals(name)) {
                aliases.add(alias);
            }
        }
        if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
            BeanFactory parentBeanFactory = getParentBeanFactory();
            if (parentBeanFactory != null) {
                aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
            }
        }
        return StringUtils.toStringArray(aliases);
    }


    //---------------------------------------------------------------------
    // Implementation of HierarchicalBeanFactory interface
    //---------------------------------------------------------------------

    public BeanFactory getParentBeanFactory() {
        return this.parentBeanFactory;
    }

    public boolean containsLocalBean(String name) {
        String beanName = transformedBeanName(name);
        return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
                (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
    }


    //---------------------------------------------------------------------
    // Implementation of ConfigurableBeanFactory interface
    //---------------------------------------------------------------------

    public void setParentBeanFactory(BeanFactory parentBeanFactory) {
        if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
            throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
        }
        this.parentBeanFactory = parentBeanFactory;
    }

    public void setBeanClassLoader(ClassLoader beanClassLoader) {
        this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
    }

    public ClassLoader getBeanClassLoader() {
        return this.beanClassLoader;
    }

    public void setTempClassLoader(ClassLoader tempClassLoader) {
        this.tempClassLoader = tempClassLoader;
    }

    public ClassLoader getTempClassLoader() {
        return this.tempClassLoader;
    }

    public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
        this.cacheBeanMetadata = cacheBeanMetadata;
    }

    public boolean isCacheBeanMetadata() {
        return this.cacheBeanMetadata;
    }

    public void setBeanExpressionResolver(BeanExpressionResolver resolver) {
        this.beanExpressionResolver = resolver;
    }

    public BeanExpressionResolver getBeanExpressionResolver() {
        return this.beanExpressionResolver;
    }

    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    public ConversionService getConversionService() {
        return this.conversionService;
    }

    public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
        Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
        this.propertyEditorRegistrars.add(registrar);
    }

    /**
     * Return the set of PropertyEditorRegistrars.
     */
    public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
        return this.propertyEditorRegistrars;
    }

    public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
        Assert.notNull(requiredType, "Required type must not be null");
        Assert.isAssignable(PropertyEditor.class, propertyEditorClass);
        this.customEditors.put(requiredType, propertyEditorClass);
    }

    public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
        registerCustomEditors(registry);
    }

    /**
     * Return the map of custom editors, with Classes as keys and PropertyEditor classes as values.
     */
    public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
        return this.customEditors;
    }

    public void setTypeConverter(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    /**
     * Return the custom TypeConverter to use, if any.
     *
     * @return the custom TypeConverter, or {@code null} if none specified
     */
    protected TypeConverter getCustomTypeConverter() {
        return this.typeConverter;
    }

    public TypeConverter getTypeConverter() {
        TypeConverter customConverter = getCustomTypeConverter();
        if (customConverter != null) {
            return customConverter;
        } else {
            // Build default TypeConverter, registering custom editors.
            SimpleTypeConverter typeConverter = new SimpleTypeConverter();
            typeConverter.setConversionService(getConversionService());
            registerCustomEditors(typeConverter);
            return typeConverter;
        }
    }

    public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
        Assert.notNull(valueResolver, "StringValueResolver must not be null");
        this.embeddedValueResolvers.add(valueResolver);
    }

    public String resolveEmbeddedValue(String value) {
        String result = value;
        for (StringValueResolver resolver : this.embeddedValueResolvers) {
            if (result == null) {
                return null;
            }
            result = resolver.resolveStringValue(result);
        }
        return result;
    }

    public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
        Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
        this.beanPostProcessors.remove(beanPostProcessor);
        this.beanPostProcessors.add(beanPostProcessor);
        if (beanPostProcessor instanceof InstantiationAwareBeanPostProcessor) {
            this.hasInstantiationAwareBeanPostProcessors = true;
        }
        if (beanPostProcessor instanceof DestructionAwareBeanPostProcessor) {
            this.hasDestructionAwareBeanPostProcessors = true;
        }
    }

    public int getBeanPostProcessorCount() {
        return this.beanPostProcessors.size();
    }

    /**
     * Return the list of BeanPostProcessors that will get applied
     * to beans created with this factory.
     * <p>
     * 返回将应用于此工厂创建的bean的后置处理区列表。
     */
    public List<BeanPostProcessor> getBeanPostProcessors() {
        return this.beanPostProcessors;
    }

    /**
     * Return whether this factory holds a InstantiationAwareBeanPostProcessor
     * that will get applied to singleton beans on shutdown.
     * <p>
     * 返回该工厂是否持有实例化感知bean后置处理器,该处理器将应用于bean.
     *
     * @see #addBeanPostProcessor
     * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
     */
    protected boolean hasInstantiationAwareBeanPostProcessors() {
        return this.hasInstantiationAwareBeanPostProcessors;
    }

    /**
     * Return whether this factory holds a DestructionAwareBeanPostProcessor
     * that will get applied to singleton beans on shutdown.
     * 返回该工厂是否持有构造bean的感知后处理器,将会应用在单例bean
     *
     * @see #addBeanPostProcessor
     * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
     */
    protected boolean hasDestructionAwareBeanPostProcessors() {
        return this.hasDestructionAwareBeanPostProcessors;
    }

    public void registerScope(String scopeName, Scope scope) {
        Assert.notNull(scopeName, "Scope identifier must not be null");
        Assert.notNull(scope, "Scope must not be null");
        if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
            throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
        }
        this.scopes.put(scopeName, scope);
    }

    public String[] getRegisteredScopeNames() {
        return StringUtils.toStringArray(this.scopes.keySet());
    }

    public Scope getRegisteredScope(String scopeName) {
        Assert.notNull(scopeName, "Scope identifier must not be null");
        return this.scopes.get(scopeName);
    }

    /**
     * Set the security context provider for this bean factory. If a security manager
     * is set, interaction with the user code will be executed using the privileged
     * of the provided security context.
     * <p>
     * 安全相关
     */
    public void setSecurityContextProvider(SecurityContextProvider securityProvider) {
        this.securityContextProvider = securityProvider;
    }

    /**
     * Delegate the creation of the access control context to the
     * {@link #setSecurityContextProvider SecurityContextProvider}.
     * <p>
     * 安全相关
     */
    @Override
    public AccessControlContext getAccessControlContext() {
        return (this.securityContextProvider != null ?
                this.securityContextProvider.getAccessControlContext() :
                AccessController.getContext());
    }

    public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
        Assert.notNull(otherFactory, "BeanFactory must not be null");
        setBeanClassLoader(otherFactory.getBeanClassLoader());
        setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
        setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
        if (otherFactory instanceof AbstractBeanFactory) {
            AbstractBeanFactory otherAbstractFactory = (AbstractBeanFactory) otherFactory;
            this.customEditors.putAll(otherAbstractFactory.customEditors);
            this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
            this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
            this.hasInstantiationAwareBeanPostProcessors = this.hasInstantiationAwareBeanPostProcessors ||
                    otherAbstractFactory.hasInstantiationAwareBeanPostProcessors;
            this.hasDestructionAwareBeanPostProcessors = this.hasDestructionAwareBeanPostProcessors ||
                    otherAbstractFactory.hasDestructionAwareBeanPostProcessors;
            this.scopes.putAll(otherAbstractFactory.scopes);
            this.securityContextProvider = otherAbstractFactory.securityContextProvider;
        } else {
            setTypeConverter(otherFactory.getTypeConverter());
        }
    }

    /**
     * Return a 'merged' BeanDefinition for the given bean name,
     * merging a child bean definition with its parent if necessary.
     * <p>This {@code getMergedBeanDefinition} considers bean definition
     * in ancestors as well.
     * 返回指定名称的一个合并的BeanDefinition,
     * 如果需要将一个子BeanDefinition和其父BeanDefinition合并.
     * 这个{@code getMergedBeanDefinition}还考虑了祖先中的bean定义
     *
     * @param name the name of the bean to retrieve the merged definition for
     *             (may be an alias)
     * @return a (potentially merged) RootBeanDefinition for the given bean
     * @throws NoSuchBeanDefinitionException if there is no bean with the given name
     * @throws BeanDefinitionStoreException  in case of an invalid bean definition
     */
    public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
        String beanName = transformedBeanName(name);

        // Efficiently check whether bean definition exists in this factory.
        // 校验当前工厂中是否存在bean的定义
        if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
            return ((ConfigurableBeanFactory) getParentBeanFactory()).getMergedBeanDefinition(beanName);
        }
        // Resolve merged bean definition locally.
        // 在本地解析合并BeanDefinition
        return getMergedLocalBeanDefinition(beanName);
    }

    public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
        String beanName = transformedBeanName(name);

        Object beanInstance = getSingleton(beanName, false);
        if (beanInstance != null) {
            return (beanInstance instanceof FactoryBean);
        } else if (containsSingleton(beanName)) {
            // null instance registered
            return false;
        }

        // No singleton instance found -> check bean definition.
        if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
            // No bean definition found in this factory -> delegate to parent.
            return ((ConfigurableBeanFactory) getParentBeanFactory()).isFactoryBean(name);
        }

        return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
    }

    @Override
    public boolean isActuallyInCreation(String beanName) {
        return isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName);
    }

    /**
     * Return whether the specified prototype bean is currently in creation
     * 返回指定的原型bean是否在创建中
     * (within the current thread).
     * // 在当前线程中
     *
     * @param beanName the name of the bean
     */
    protected boolean isPrototypeCurrentlyInCreation(String beanName) {
        Object curVal = this.prototypesCurrentlyInCreation.get();
        return (curVal != null &&
                (curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
    }

    /**
     * Callback before prototype creation.
     * <p>The default implementation register the prototype as currently in creation.
     * <p>
     * 在原型模式创建前回调
     * 默认实现将原型注册为当前创建的原型
     *
     * @param beanName the name of the prototype about to be created
     * @see #isPrototypeCurrentlyInCreation
     */
    @SuppressWarnings("unchecked")
    protected void beforePrototypeCreation(String beanName) {
        Object curVal = this.prototypesCurrentlyInCreation.get();
        if (curVal == null) {
            this.prototypesCurrentlyInCreation.set(beanName);
        } else if (curVal instanceof String) {
            Set<String> beanNameSet = new HashSet<String>(2);
            beanNameSet.add((String) curVal);
            beanNameSet.add(beanName);
            this.prototypesCurrentlyInCreation.set(beanNameSet);
        } else {
            Set<String> beanNameSet = (Set<String>) curVal;
            beanNameSet.add(beanName);
        }
    }

    /**
     * Callback after prototype creation.
     * <p>The default implementation marks the prototype as not in creation anymore.
     * <p>
     * 在原型创建后回调
     * 默认实现是原型模式不在创建
     *
     * @param beanName the name of the prototype that has been created
     * @see #isPrototypeCurrentlyInCreation
     */
    @SuppressWarnings("unchecked")
    protected void afterPrototypeCreation(String beanName) {
        Object curVal = this.prototypesCurrentlyInCreation.get();
        if (curVal instanceof String) {
            this.prototypesCurrentlyInCreation.remove();
        } else if (curVal instanceof Set) {
            Set<String> beanNameSet = (Set<String>) curVal;
            beanNameSet.remove(beanName);
            if (beanNameSet.isEmpty()) {
                this.prototypesCurrentlyInCreation.remove();
            }
        }
    }

    public void destroyBean(String beanName, Object beanInstance) {
        destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
    }

    /**
     * Destroy the given bean instance (usually a prototype instance
     * obtained from this factory) according to the given bean definition.
     * <p>
     * 根据给定beanDefinition销毁给定bean的实例(通常是这个工厂获得的原型实例)
     *
     * @param beanName     the name of the bean definition
     * @param beanInstance the bean instance to destroy
     * @param mbd          the merged bean definition
     */
    protected void destroyBean(String beanName, Object beanInstance, RootBeanDefinition mbd) {
        new DisposableBeanAdapter(beanInstance, beanName, mbd, getBeanPostProcessors(), getAccessControlContext()).destroy();
    }

    public void destroyScopedBean(String beanName) {
        RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
        if (mbd.isSingleton() || mbd.isPrototype()) {
            throw new IllegalArgumentException(
                    "Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
        }
        String scopeName = mbd.getScope();
        Scope scope = this.scopes.get(scopeName);
        if (scope == null) {
            throw new IllegalStateException("No Scope SPI registered for scope '" + scopeName + "'");
        }
        Object bean = scope.remove(beanName);
        if (bean != null) {
            destroyBean(beanName, bean, mbd);
        }
    }


    //---------------------------------------------------------------------
    // Implementation methods
    //---------------------------------------------------------------------

    /**
     * Return the bean name, stripping out the factory dereference prefix if necessary,
     * and resolving aliases to canonical names.
     * <p>
     * 返回bean名称,如果是beanFactory需要解析前缀&,并将别名解析为规范名称。
     *
     * @param name the user-specified name
     * @return the transformed bean name
     */
    protected String transformedBeanName(String name) {
        return canonicalName(BeanFactoryUtils.transformedBeanName(name));
    }

    /**
     * Determine the original bean name, resolving locally defined aliases to canonical names.
     * <p>
     * 确定原始的bean名称,将本工厂定义的别名解析为规范的名称
     *
     * @param name the user-specified name
     * @return the original bean name
     */
    protected String originalBeanName(String name) {
        String beanName = transformedBeanName(name);
        if (name.startsWith(FACTORY_BEAN_PREFIX)) {
            beanName = FACTORY_BEAN_PREFIX + beanName;
        }
        return beanName;
    }

    /**
     * Initialize the given BeanWrapper with the custom editors registered
     * with this factory. To be called for BeanWrappers that will create
     * and populate bean instances.
     * <p>
     * 使用在此工厂注册的自定义属性编辑器初始化给定的BeanWrapper.在创建或者填充bean实例属性的时候BeanWrapper被调用
     * <p>
     * <p>
     * <p>The default implementation delegates to {@link #registerCustomEditors}.
     * Can be overridden in subclasses.
     *
     * @param bw the BeanWrapper to initialize
     */
    protected void initBeanWrapper(BeanWrapper bw) {
        bw.setConversionService(getConversionService());
        registerCustomEditors(bw);
    }

    /**
     * Initialize the given PropertyEditorRegistry with the custom editors
     * that have been registered with this BeanFactory.
     * <p>To be called for BeanWrappers that will create and populate bean
     * instances, and for SimpleTypeConverter used for constructor argument
     * and factory method type conversion.
     * <p>
     * 使用已在此BeanFactory注册的自定义编辑器初始化给定的PropertyEditorRegistry.
     * <p>
     * BeanWrappers在创建和填充bean实例的的时候被调用,以及SimpleTypeConverter被构造函数和工厂方法类型转换时.
     *
     * @param registry the PropertyEditorRegistry to initialize
     */
    protected void registerCustomEditors(PropertyEditorRegistry registry) {
        PropertyEditorRegistrySupport registrySupport =
                (registry instanceof PropertyEditorRegistrySupport ? (PropertyEditorRegistrySupport) registry : null);
        if (registrySupport != null) {
            registrySupport.useConfigValueEditors();
        }
        if (!this.propertyEditorRegistrars.isEmpty()) {
            for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
                try {
                    registrar.registerCustomEditors(registry);
                } catch (BeanCreationException ex) {
                    Throwable rootCause = ex.getMostSpecificCause();
                    if (rootCause instanceof BeanCurrentlyInCreationException) {
                        BeanCreationException bce = (BeanCreationException) rootCause;
                        if (isCurrentlyInCreation(bce.getBeanName())) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
                                        "] failed because it tried to obtain currently created bean '" +
                                        ex.getBeanName() + "': " + ex.getMessage());
                            }
                            onSuppressedException(ex);
                            continue;
                        }
                    }
                    throw ex;
                }
            }
        }
        if (!this.customEditors.isEmpty()) {
            for (Map.Entry<Class<?>, Class<? extends PropertyEditor>> entry : this.customEditors.entrySet()) {
                Class<?> requiredType = entry.getKey();
                Class<? extends PropertyEditor> editorClass = entry.getValue();
                registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass));
            }
        }
    }


    /**
     * Return a merged RootBeanDefinition, traversing the parent bean definition
     * if the specified bean corresponds to a child bean definition.
     * <p>
     * 返回一个合并的RootBeanDefinition,如果指定的bean对应于一个子bean定义,则遍历父bean定义。
     *
     * @param beanName the name of the bean to retrieve the merged definition for
     * @return a (potentially merged) RootBeanDefinition for the given bean
     * @throws NoSuchBeanDefinitionException if there is no bean with the given name
     * @throws BeanDefinitionStoreException  in case of an invalid bean definition
     */
    protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
        // Quick check on the concurrent map first, with minimal locking.
        RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
        if (mbd != null) {
            return mbd;
        }
        return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
    }

    /**
     * Return a RootBeanDefinition for the given top-level bean, by merging with
     * the parent if the given bean's definition is a child bean definition.
     * <p>
     * 如果给定bean的定义是子bean定义,那么通过与父bean合并,返回给定顶级bean的RootBeanDefinition。
     *
     * @param beanName the name of the bean definition
     * @param bd       the original bean definition (Root/ChildBeanDefinition)
     * @return a (potentially merged) RootBeanDefinition for the given bean
     * @throws BeanDefinitionStoreException in case of an invalid bean definition
     */
    protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
            throws BeanDefinitionStoreException {

        return getMergedBeanDefinition(beanName, bd, null);
    }

    /**
     * Return a RootBeanDefinition for the given bean, by merging with the
     * parent if the given bean's definition is a child bean definition.
     * <p>
     * 如果给定bean的定义是一个子bean定义,则通过与父bean合并返回给定bean的定义。
     *
     * @param beanName     the name of the bean definition
     * @param bd           the original bean definition (Root/ChildBeanDefinition)
     * @param containingBd the containing bean definition in case of inner bean,
     *                     or {@code null} in case of a top-level bean
     * @return a (potentially merged) RootBeanDefinition for the given bean
     * @throws BeanDefinitionStoreException in case of an invalid bean definition
     */
    protected RootBeanDefinition getMergedBeanDefinition(
            String beanName, BeanDefinition bd, BeanDefinition containingBd)
            throws BeanDefinitionStoreException {

        synchronized (this.mergedBeanDefinitions) {
            RootBeanDefinition mbd = null;

            // Check with full lock now in order to enforce the same merged instance.
            if (containingBd == null) {
                mbd = this.mergedBeanDefinitions.get(beanName);
            }

            if (mbd == null) {
                if (bd.getParentName() == null) {
                    // Use copy of given root bean definition.
                    if (bd instanceof RootBeanDefinition) {
                        mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
                    } else {
                        mbd = new RootBeanDefinition(bd);
                    }
                } else {
                    // Child bean definition: needs to be merged with parent.
                    BeanDefinition pbd;
                    try {
                        String parentBeanName = transformedBeanName(bd.getParentName());
                        if (!beanName.equals(parentBeanName)) {
                            pbd = getMergedBeanDefinition(parentBeanName);
                        } else {
                            if (getParentBeanFactory() instanceof ConfigurableBeanFactory) {
                                pbd = ((ConfigurableBeanFactory) getParentBeanFactory()).getMergedBeanDefinition(parentBeanName);
                            } else {
                                throw new NoSuchBeanDefinitionException(bd.getParentName(),
                                        "Parent name '" + bd.getParentName() + "' is equal to bean name '" + beanName +
                                                "': cannot be resolved without an AbstractBeanFactory parent");
                            }
                        }
                    } catch (NoSuchBeanDefinitionException ex) {
                        throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
                                "Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
                    }
                    // Deep copy with overridden values.
                    mbd = new RootBeanDefinition(pbd);
                    mbd.overrideFrom(bd);
                }

                // Set default singleton scope, if not configured before.
                if (!StringUtils.hasLength(mbd.getScope())) {
                    mbd.setScope(RootBeanDefinition.SCOPE_SINGLETON);
                }

                // A bean contained in a non-singleton bean cannot be a singleton itself.
                // Let's correct this on the fly here, since this might be the result of
                // parent-child merging for the outer bean, in which case the original inner bean
                // definition will not have inherited the merged outer bean's singleton status.
                if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
                    mbd.setScope(containingBd.getScope());
                }

                // Only cache the merged bean definition if we're already about to create an
                // instance of the bean, or at least have already created an instance before.
                if (containingBd == null && isCacheBeanMetadata() && isBeanEligibleForMetadataCaching(beanName)) {
                    this.mergedBeanDefinitions.put(beanName, mbd);
                }
            }

            return mbd;
        }
    }

    /**
     * Check the given merged bean definition,
     * potentially throwing validation exceptions.
     * <p>
     * 检查给定的合并bean定义,可能会抛出验证异常。
     *
     * @param mbd      the merged bean definition to check
     * @param beanName the name of the bean
     * @param args     the arguments for bean creation, if any
     * @throws BeanDefinitionStoreException in case of validation failure
     */
    protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, Object[] args)
            throws BeanDefinitionStoreException {

        // check if bean definition is not abstract
        // 校验beanDefinition是否是抽象类
        if (mbd.isAbstract()) {
            throw new BeanIsAbstractException(beanName);
        }

        // Check validity of the usage of the args parameter. This can
        // only be used for prototypes constructed via a factory method.
        // 检查args参数使用的有效性.这只能用于通过工厂方法构建的原型模式。
        if (args != null && !mbd.isPrototype()) {
            throw new BeanDefinitionStoreException(
                    "Can only specify arguments for the getBean method when referring to a prototype bean definition");
        }
    }

    /**
     * Remove the merged bean definition for the specified bean,
     * recreating it on next access.
     * <p>
     * 删除指定bean的合并bean定义,在下次访问时重新创建它。
     *
     * @param beanName the bean name to clear the merged definition for
     */
    protected void clearMergedBeanDefinition(String beanName) {
        this.mergedBeanDefinitions.remove(beanName);
    }

    /**
     * Resolve the bean class for the specified bean definition,
     * resolving a bean class name into a Class reference (if necessary)
     * and storing the resolved Class in the bean definition for further use.
     * <p>
     * 解析指定bean定义的bean类,将bean类名称解析为类引用(如果需要),并将解析后的类存储在bean定义中以供进一步使用。
     *
     * @param mbd          the merged bean definition to determine the class for
     * @param beanName     the name of the bean (for error handling purposes)
     * @param typesToMatch the types to match in case of internal type matching purposes
     *                     (also signals that the returned {@code Class} will never be exposed to application code)
     * @return the resolved bean class (or {@code null} if none)
     * @throws CannotLoadBeanClassException if we failed to load the class
     */
    protected Class<?> resolveBeanClass(final RootBeanDefinition mbd, String beanName, final Class<?>... typesToMatch)
            throws CannotLoadBeanClassException {
        try {
            if (mbd.hasBeanClass()) {
                return mbd.getBeanClass();
            }
            if (System.getSecurityManager() != null) {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<Class<?>>() {
                    public Class<?> run() throws Exception {
                        return doResolveBeanClass(mbd, typesToMatch);
                    }
                }, getAccessControlContext());
            } else {
                return doResolveBeanClass(mbd, typesToMatch);
            }
        } catch (PrivilegedActionException pae) {
            ClassNotFoundException ex = (ClassNotFoundException) pae.getException();
            throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
        } catch (ClassNotFoundException ex) {
            throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
        } catch (LinkageError err) {
            throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
        }
    }

    private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch) throws ClassNotFoundException {
        if (!ObjectUtils.isEmpty(typesToMatch)) {
            ClassLoader tempClassLoader = getTempClassLoader();
            if (tempClassLoader != null) {
                if (tempClassLoader instanceof DecoratingClassLoader) {
                    DecoratingClassLoader dcl = (DecoratingClassLoader) tempClassLoader;
                    for (Class<?> typeToMatch : typesToMatch) {
                        dcl.excludeClass(typeToMatch.getName());
                    }
                }
                String className = mbd.getBeanClassName();
                return (className != null ? ClassUtils.forName(className, tempClassLoader) : null);
            }
        }
        return mbd.resolveBeanClass(getBeanClassLoader());
    }

    /**
     * Evaluate the given String as contained in a bean definition,
     * potentially resolving it as an expression.
     * <p>
     * 计算bean定义中包含的给定字符串,可能将其解析为表达式。
     *
     * @param value          the value to check
     * @param beanDefinition the bean definition that the value comes from
     * @return the resolved value
     * @see #setBeanExpressionResolver
     */
    protected Object evaluateBeanDefinitionString(String value, BeanDefinition beanDefinition) {
        if (this.beanExpressionResolver == null) {
            return value;
        }
        Scope scope = (beanDefinition != null ? getRegisteredScope(beanDefinition.getScope()) : null);
        return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
    }


    /**
     * Predict the eventual bean type (of the processed bean instance) for the
     * specified bean. Called by {@link #getType} and {@link #isTypeMatch}.
     * Does not need to handle FactoryBeans specifically, since it is only
     * supposed to operate on the raw bean type.
     * <p>
     * 预测指定bean的类型(经过处理的bean实例的类型).由{@link #getType} 和 {@link #isTypeMatch}调用
     * 不需要专门处理FactoryBeans,因为他只对原始bean类型操作
     * <p>
     * <p>This implementation is simplistic in that it is not able to
     * handle factory methods and InstantiationAwareBeanPostProcessors.
     * It only predicts the bean type correctly for a standard bean.
     * To be overridden in subclasses, applying more sophisticated type detection.
     * <p>
     * 这个类的实现很简单,因为他不能处理工厂方法和InstantiationAwareBeanPostProcessors.
     * 他只能正确预测标准的bean.当应用复杂的时候需要在子类中重写
     *
     * @param beanName     the name of the bean
     * @param mbd          the merged bean definition to determine the type for
     * @param typesToMatch the types to match in case of internal type matching purposes
     *                     (also signals that the returned {@code Class} will never be exposed to application code)
     * @return the type of the bean, or {@code null} if not predictable
     */
    protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
        if (mbd.getFactoryMethodName() != null) {
            return null;
        }
        return resolveBeanClass(mbd, beanName, typesToMatch);
    }

    /**
     * Check whether the given bean is defined as a {@link FactoryBean}.
     * <p>
     * 校验给定的bean是否是{@link FactoryBean}
     *
     * @param beanName the name of the bean
     * @param mbd      the corresponding bean definition
     */
    protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
        Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
        return (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
    }

    /**
     * Determine the bean type for the given FactoryBean definition, as far as possible.
     * Only called if there is no singleton instance registered for the target bean already.
     * <p>The default implementation creates the FactoryBean via {@code getBean}
     * to call its {@code getObjectType} method. Subclasses are encouraged to optimize
     * this, typically by just instantiating the FactoryBean but not populating it yet,
     * trying whether its {@code getObjectType} method already returns a type.
     * If no type found, a full FactoryBean creation as performed by this implementation
     * should be used as fallback.
     * <p>
     * 尽可能地为给定的FactoryBean定义确定bean类型.只有在没有为目标bean注册的singleton实例时才调用.
     * // todo 这块没太懂
     *
     * @param beanName the name of the bean
     * @param mbd      the merged bean definition for the bean
     * @return the type for the bean if determinable, or {@code null} else
     * @see org.springframework.beans.factory.FactoryBean#getObjectType()
     * @see #getBean(String)
     */
    protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
        if (!mbd.isSingleton()) {
            return null;
        }
        try {
            FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
            return getTypeForFactoryBean(factoryBean);
        } catch (BeanCreationException ex) {
            // Can only happen when getting a FactoryBean.
            if (logger.isDebugEnabled()) {
                logger.debug("Ignoring bean creation exception on FactoryBean type check: " + ex);
            }
            onSuppressedException(ex);
            return null;
        }
    }

    /**
     * Mark the specified bean as already created (or about to be created).
     * <p>This allows the bean factory to optimize its caching for repeated
     * creation of the specified bean.
     * <p>
     * 将指定的bean标记为已经创建的(或即将创建的).
     * 这允许bean工厂优化其缓存,以便重复创建指定的bean
     *
     * @param beanName the name of the bean
     */
    protected void markBeanAsCreated(String beanName) {
        this.alreadyCreated.put(beanName, Boolean.TRUE);
    }

    /**
     * Perform appropriate cleanup of cached metadata after bean creation failed.
     * <p>
     * 在bean创建失败后,对缓存的元数据执行适当的清理
     *
     * @param beanName the name of the bean
     */
    protected void cleanupAfterBeanCreationFailure(String beanName) {
        this.alreadyCreated.remove(beanName);
    }

    /**
     * Determine whether the specified bean is eligible for having
     * its bean definition metadata cached.
     * <p>
     * 确定指定的bean是否适合缓存其bean定义元数据
     *
     * @param beanName the name of the bean
     * @return {@code true} if the bean's metadata may be cached
     * at this point already
     */
    protected boolean isBeanEligibleForMetadataCaching(String beanName) {
        return this.alreadyCreated.containsKey(beanName);
    }

    /**
     * Remove the singleton instance (if any) for the given bean name,
     * but only if it hasn't been used for other purposes than type checking.
     * <p>
     * 为给定的bean名称删除单例实例(如果有的话),但前提是它没有用于类型检查以外的其他目的.
     *
     * @param beanName the name of the bean
     * @return {@code true} if actually removed, {@code false} otherwise
     */
    protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
        if (!this.alreadyCreated.containsKey(beanName)) {
            removeSingleton(beanName);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get the object for the given bean instance, either the bean
     * instance itself or its created object in case of a FactoryBean.
     * <p>
     * 获取给定bean实例的对象,对于FactoryBean,可能是bean实例本身,也可能是它创建的对象。
     *
     * @param beanInstance the shared bean instance
     * @param name         name that may include factory dereference prefix
     * @param beanName     the canonical bean name
     * @param mbd          the merged bean definition
     * @return the object to expose for the bean
     */
    protected Object getObjectForBeanInstance(
            Object beanInstance, String name, String beanName, RootBeanDefinition mbd) {

        // Don't let calling code try to dereference the factory if the bean isn't a factory.
        if (BeanFactoryUtils.isFactoryDereference(name) && !(beanInstance instanceof FactoryBean)) {
            throw new BeanIsNotAFactoryException(transformedBeanName(name), beanInstance.getClass());
        }

        // Now we have the bean instance, which may be a normal bean or a FactoryBean.
        // If it's a FactoryBean, we use it to create a bean instance, unless the
        // caller actually wants a reference to the factory.
        if (!(beanInstance instanceof FactoryBean) || BeanFactoryUtils.isFactoryDereference(name)) {
            return beanInstance;
        }

        Object object = null;
        if (mbd == null) {
            object = getCachedObjectForFactoryBean(beanName);
        }
        if (object == null) {
            // Return bean instance from factory.
            FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
            // Caches object obtained from FactoryBean if it is a singleton.
            if (mbd == null && containsBeanDefinition(beanName)) {
                mbd = getMergedLocalBeanDefinition(beanName);
            }
            boolean synthetic = (mbd != null && mbd.isSynthetic());
            object = getObjectFromFactoryBean(factory, beanName, !synthetic);
        }
        return object;
    }

    /**
     * Determine whether the given bean name is already in use within this factory,
     * i.e. whether there is a local bean or alias registered under this name or
     * an inner bean created with this name.
     *
     * 确定给定的bean名称是否已经在此工厂中使用,
     * 无论在这个名称下注册的是本地bean还是别名,还是用这个名称创建的内部bean
     *
     * @param beanName the name to check
     */
    public boolean isBeanNameInUse(String beanName) {
        return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
    }

    /**
     * Determine whether the given bean requires destruction on shutdown.
     * <p>The default implementation checks the DisposableBean interface as well as
     * a specified destroy method and registered DestructionAwareBeanPostProcessors.
     *
     * 确定给定bean是否需要在关机时销毁.
     * 默认实现检查DisposableBean接口以及指定的销毁方法和注册销毁感知bean后处理器
     *
     * @param bean the bean instance to check
     * @param mbd  the corresponding bean definition
     * @see org.springframework.beans.factory.DisposableBean
     * @see AbstractBeanDefinition#getDestroyMethodName()
     * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
     */
    protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
        return (bean != null &&
                (DisposableBeanAdapter.hasDestroyMethod(bean, mbd) || hasDestructionAwareBeanPostProcessors()));
    }

    /**
     * Add the given bean to the list of disposable beans in this factory,
     * registering its DisposableBean interface and/or the given destroy method
     * to be called on factory shutdown (if applicable). Only applies to singletons.
     *
     * 将给定bean添加到工厂中的DisposableBean列表中,注册其DisposableBean接口和/或在工厂关闭时调用的给定销毁方法(如果适用).
     * 只适用于单例
     *
     *
     * @param beanName the name of the bean
     * @param bean     the bean instance
     * @param mbd      the bean definition for the bean
     * @see RootBeanDefinition#isSingleton
     * @see RootBeanDefinition#getDependsOn
     * @see #registerDisposableBean
     * @see #registerDependentBean
     */
    protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
        AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
        if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
            if (mbd.isSingleton()) {
                // Register a DisposableBean implementation that performs all destruction
                // work for the given bean: DestructionAwareBeanPostProcessors,
                // DisposableBean interface, custom destroy method.
                registerDisposableBean(beanName,
                        new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
            } else {
                // A bean with a custom scope...
                Scope scope = this.scopes.get(mbd.getScope());
                if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope '" + mbd.getScope() + "'");
                }
                scope.registerDestructionCallback(beanName,
                        new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
            }
        }
    }


    //---------------------------------------------------------------------
    // Abstract methods to be implemented by subclasses
    //---------------------------------------------------------------------

    /**
     * Check if this bean factory contains a bean definition with the given name.
     * Does not consider any hierarchy this factory may participate in.
     *
     * 检查此bean工厂是否包含具有给定名称的bean定义.不考虑此工厂可能参与的任何层次结构.
     *
     * 当没有找到缓存的单例实例时,由{@code containsBean}调用.
     * 根据具体bean工厂实现的性质,这种操作可能很昂贵(例如,由于在外部注册中心中进行目录查找).
     * 然而,对于可列表bean工厂,这通常只相当于本地散列查找:因此操作是公共接口的一部分。
     * 在这种情况下,相同的实现可以同时用于此模板方法和公共接口方法.
     *
     *
     * Invoked by {@code containsBean} when no cached singleton instance is found.
     * <p>Depending on the nature of the concrete bean factory implementation,
     * this operation might be expensive (for example, because of directory lookups
     * in external registries). However, for listable bean factories, this usually
     * just amounts to a local hash lookup: The operation is therefore part of the
     * public interface there. The same implementation can serve for both this
     * template method and the public interface method in that case.
     *
     * @param beanName the name of the bean to look for
     * @return if this bean factory contains a bean definition with the given name
     * @see #containsBean
     * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
     */
    protected abstract boolean containsBeanDefinition(String beanName);

    /**
     * Return the bean definition for the given bean name.
     * Subclasses should normally implement caching, as this method is invoked
     * by this class every time bean definition metadata is needed.
     * <p>Depending on the nature of the concrete bean factory implementation,
     * this operation might be expensive (for example, because of directory lookups
     * in external registries). However, for listable bean factories, this usually
     * just amounts to a local hash lookup: The operation is therefore part of the
     * public interface there. The same implementation can serve for both this
     * template method and the public interface method in that case.
     *
     * @param beanName the name of the bean to find a definition for
     * @return the BeanDefinition for this prototype name (never {@code null})
     * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if the bean definition cannot be resolved
     * @throws BeansException                                                  in case of errors
     * @see RootBeanDefinition
     * @see ChildBeanDefinition
     * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#getBeanDefinition
     */
    protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

    /**
     * Create a bean instance for the given bean definition.
     * The bean definition will already have been merged with the parent
     * definition in case of a child definition.
     * <p>All the other methods in this class invoke this method, although
     * beans may be cached after being instantiated by this method. All bean
     * instantiation within this class is performed by this method.
     *
     * 为给定的bean定义创建一个bean实例.对于子定义,bean定义已经与父定义合并。
     * <p>这个类中的所有其他方法都调用这个方法，尽管bean可能在被这个方法实例化之后被缓存。
     * 这个类中的所有bean实例化都是由这个方法执行的。
     *
     *
     * @param beanName the name of the bean
     * @param mbd      the merged bean definition for the bean
     * @param args     arguments to use if creating a prototype using explicit arguments to a
     *                 static factory method. This parameter must be {@code null} except in this case.
     * @return a new instance of the bean
     * @throws BeanCreationException if the bean could not be created
     */
    protected abstract Object createBean(String beanName, RootBeanDefinition mbd, Object[] args)
            throws BeanCreationException;

}
