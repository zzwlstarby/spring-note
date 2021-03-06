/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.beans.factory;

import java.lang.annotation.Annotation;
import java.util.Map;

import org.springframework.beans.BeansException;

/**
 * Extension of the {@link BeanFactory} interface to be implemented by bean factories
 * that can enumerate all their bean instances, rather than attempting bean lookup
 * by name one by one as requested by clients. BeanFactory implementations that
 * preload all their bean definitions (such as XML-based factories) may implement
 * this interface.
 *
 * {@link BeanFactory}接口的扩展,如果实现该接口的bean工厂可以枚举出所有bean的实例,
 * 而不是通过调用方一个一个通过名称去查找,bean工厂实现预加载所有beanDefinitions可以实现该接口
 *
 *
 * <p>If this is a {@link HierarchicalBeanFactory}, the return values will <i>not</i>
 * take any BeanFactory hierarchy into account, but will relate only to the beans
 * defined in the current factory. Use the {@link BeanFactoryUtils} helper class
 * to consider beans in ancestor factories too.
 *
 * 如果是一个{@link HierarchicalBeanFactory} 类,返回的值将不会考虑父工厂中定义的bean,
 * 只会返回与当前工厂相关的定义.{@link BeanFactoryUtils} 这个类会考虑祖先工厂中定义的bean
 *
 *
 * <p>The methods in this interface will just respect bean definitions of this factory.
 * They will ignore any singleton beans that have been registered by other means like
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}'s
 * {@code registerSingleton} method, with the exception of
 * {@code getBeanNamesOfType} and {@code getBeansOfType} which will check
 * such manually registered singletons too. Of course, BeanFactory's {@code getBean}
 * does allow transparent access to such special beans as well. However, in typical
 * scenarios, all beans will be defined by external bean definitions anyway, so most
 * applications don't need to worry about this differentation.
 *
 * 该接口中的方法只会考虑当前工厂中的bean定义,他们会忽略其他方式注册的单例bean比如:
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory},
 * {@code registerSingleton} 方法,
 * 除了{@code getBeanNamesOfType} and {@code getBeansOfType}将会检查手动注册的单例
 * 当前, BeanFactory's {@code getBean}方法也允许透明的访问这些特殊的bean.然而在典型
 * 场景下,所有的bean都是由外部定义的(如xml),所以大部分的应用程序都不需要考虑这种差异.
 *
 *
 * <p><b>NOTE:</b> With the exception of {@code getBeanDefinitionCount}
 * and {@code containsBeanDefinition}, the methods in this interface
 * are not designed for frequent invocation. Implementations may be slow.
 *
 * 除了{@code getBeanDefinitionCount} 和 {@code containsBeanDefinition}
 * 这个接口中的方法不是为了频繁调用而设计的.实现可能是比较缓慢的
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 16 April 2001
 * @see HierarchicalBeanFactory
 * @see BeanFactoryUtils
 */
public interface ListableBeanFactory extends BeanFactory {

    /**
     * Check if this bean factory contains a bean definition with the given name.
     * <p>Does not consider any hierarchy this factory may participate in,
     * and ignores any singleton beans that have been registered by
     * other means than bean definitions.
     *
     * 检查当前bean工厂是否包含给定名称的beanDefinition
     * 不会考虑任何父工厂中是定义,将会忽略已其他方式而不是通过beanDefinition注册的单例bean.
     *
     * @param beanName the name of the bean to look for
     * @return if this bean factory contains a bean definition with the given name
     * @see #containsBean
     */
    boolean containsBeanDefinition(String beanName);

    /**
     * Return the number of beans defined in the factory.
     * <p>Does not consider any hierarchy this factory may participate in,
     * and ignores any singleton beans that have been registered by
     * other means than bean definitions.
     *
     * 将会返回当前bean工厂定义的bean数量
     * 不会考虑任何父工厂中是定义,将会忽略已其他方式而不是通过beanDefinition注册的单例bean.
     *
     * @return the number of beans defined in the factory
     */
    int getBeanDefinitionCount();

    /**
     * Return the names of all beans defined in this factory.
     * <p>Does not consider any hierarchy this factory may participate in,
     * and ignores any singleton beans that have been registered by
     * other means than bean definitions.
     *
     * 将会返回当前bean工厂定义的所有bean的名称
     * 不会考虑任何父工厂中是定义,将会忽略已其他方式而不是通过beanDefinition注册的单例bean.
     *
     * @return the names of all beans defined in this factory,
     * or an empty array if none defined
     */
    String[] getBeanDefinitionNames();

    /**
     * Return the names of beans matching the given type (including subclasses),
     * judging from either bean definitions or the value of {@code getObjectType}
     * in the case of FactoryBeans.
     *
     * 返回匹配给定类型的bean的名称(包括子类),会从bean的定义或者FactoryBeans的{@code getObjectType}
     * 方法来判断
     *
     *
     * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
     * check nested beans which might match the specified type as well.
     * <p>Does consider objects created by FactoryBeans, which means that FactoryBeans
     * will get initialized. If the object created by the FactoryBean doesn't match,
     * the raw FactoryBean itself will be matched against the type.
     *
     * 这个方法只针针对顶级的bean有效,不会对内嵌bean进行校验,即使有可能和类型匹配.
     * 考虑了由FactoryBeans创建的对象,如果由FactoryBeans创建的对象不匹配,那么原始的FactoryBeans
     * 本身将和类型匹配
     *
     * <p>Does not consider any hierarchy this factory may participate in.
     * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
     * to include beans in ancestor factories too.
     *
     * 当前方法不考虑这个工厂的父工厂,可以使用BeanFactoryUtils的{@code beanNamesForTypeIncludingAncestors}
     * 方法将会包括祖先的bean
     *
     * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
     * by other means than bean definitions.
     *
     * 不会忽略由其他方式而不是通过beanDefinitions定义的bean
     *
     * <p>This version of {@code getBeanNamesForType} matches all kinds of beans,
     * be it singletons, prototypes, or FactoryBeans. In most implementations, the
     * result will be the same as for {@code getBeanNamesOfType(type, true, true)}.
     *
     * 当前版本的{@code getBeanNamesForType}会匹配所有类型的bean,包括单例,原型,或者FactoryBeans
     * 创建的bean,在大多数的实现中,当前方法的结果和{@code getBeanNamesOfType(type, true, true)}一样
     *
     * <p>Bean names returned by this method should always return bean names <i>in the
     * order of definition</i> in the backend configuration, as far as possible.
     *
     * 当前方法返回的总是bean的名称,尽可能在配置中配置定义顺序
     *
     *
     * @param type the class or interface to match, or {@code null} for all bean names
     * @return the names of beans (or objects created by FactoryBeans) matching
     * the given object type (including subclasses), or an empty array if none
     * @see FactoryBean#getObjectType
     * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class)
     */
    String[] getBeanNamesForType(Class<?> type);

    /**
     * Return the names of beans matching the given type (including subclasses),
     * judging from either bean definitions or the value of {@code getObjectType}
     * in the case of FactoryBeans.
     *
     * 返回匹配给定类型的bean的名称(包括子类),会从bean的定义或者FactoryBeans的{@code getObjectType}
     * 方法来判断
     *
     * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
     * check nested beans which might match the specified type as well.
     *
     * 这个方法只针针对顶级的bean有效,不会对内嵌bean进行校验,即使有可能和类型匹配.
     * 考虑了由FactoryBeans创建的对象,如果由FactoryBeans创建的对象不匹配,那么原始的FactoryBeans
     * 本身将和类型匹配
     *
     * <p>Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set,
     * which means that FactoryBeans will get initialized. If the object created by the
     * FactoryBean doesn't match, the raw FactoryBean itself will be matched against the
     * type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked
     * (which doesn't require initialization of each FactoryBean).
     *
     * 如果设置了allowEagerInit标志,则考虑由FactoryBeans创建的对象,这意味着FactoryBeans将会被初始化,
     * 如果由FactoryBeans创建的对象不匹配,那么原始FactoryBeans对象本身将与之匹配,如果allowEagerInit
     * 没有设置,那么智慧检查原始的FactoryBeans(因为不需要每个FactoryBeans初始化,就是没有提前初始化)
     *
     *
     * <p>Does not consider any hierarchy this factory may participate in.
     * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
     * to include beans in ancestor factories too.
     *
     * 当前方法不考虑这个工厂的父工厂,可以使用BeanFactoryUtils的{@code beanNamesForTypeIncludingAncestors}
     * 方法将会包括祖先的bean
     *
     * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
     * by other means than bean definitions.
     *
     * 不会忽略由其他方式而不是通过beanDefinitions定义的bean
     *
     * <p>Bean names returned by this method should always return bean names <i>in the
     * order of definition</i> in the backend configuration, as far as possible.
     *
     * 当前方法返回的总是bean的名称,尽可能在配置中配置定义顺序
     *
     * @param type the class or interface to match, or {@code null} for all bean names
     * @param includeNonSingletons whether to include prototype or scoped beans too
     * or just singletons (also applies to FactoryBeans)
     * @param allowEagerInit whether to initialize <i>lazy-init singletons</i> and
     * <i>objects created by FactoryBeans</i> (or by factory methods with a
     * "factory-bean" reference) for the type check. Note that FactoryBeans need to be
     * eagerly initialized to determine their type: So be aware that passing in "true"
     * for this flag will initialize FactoryBeans and "factory-bean" references.
     * @return the names of beans (or objects created by FactoryBeans) matching
     * the given object type (including subclasses), or an empty array if none
     * @see FactoryBean#getObjectType
     * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
     */
    String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit);

    /**
     * Return the bean instances that match the given object type (including
     * subclasses), judging from either bean definitions or the value of
     * {@code getObjectType} in the case of FactoryBeans.
     *
     * 返回匹配给定类型的bean的名称(包括子类),会从bean的定义或者FactoryBeans的{@code getObjectType}
     * 方法来判断
     *
     * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
     * check nested beans which might match the specified type as well.
     * <p>Does consider objects created by FactoryBeans, which means that FactoryBeans
     * will get initialized. If the object created by the FactoryBean doesn't match,
     * the raw FactoryBean itself will be matched against the type.
     *
     * 这个方法只针针对顶级的bean有效,不会对内嵌bean进行校验,即使有可能和类型匹配.
     * 考虑了由FactoryBeans创建的对象,如果由FactoryBeans创建的对象不匹配,那么原始的FactoryBeans
     * 本身将和类型匹配
     *
     * <p>Does not consider any hierarchy this factory may participate in.
     * Use BeanFactoryUtils' {@code beansOfTypeIncludingAncestors}
     * to include beans in ancestor factories too.
     *
     * 当前方法不考虑这个工厂的父工厂,可以使用BeanFactoryUtils的{@code beanNamesForTypeIncludingAncestors}
     * 方法将会包括祖先的bean
     *
     * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
     * by other means than bean definitions.
     *
     * 不会忽略由其他方式而不是通过beanDefinitions定义的bean
     *
     * <p>This version of getBeansOfType matches all kinds of beans, be it
     * singletons, prototypes, or FactoryBeans. In most implementations, the
     * result will be the same as for {@code getBeansOfType(type, true, true)}.
     *
     * 当前版本的{@code getBeanNamesForType}会匹配所有类型的bean,包括单例,原型,或者FactoryBeans
     * 创建的bean,在大多数的实现中,当前方法的结果和{@code getBeanNamesOfType(type, true, true)}一样
     *
     *
     * <p>The Map returned by this method should always return bean names and
     * corresponding bean instances <i>in the order of definition</i> in the
     * backend configuration, as far as possible.
     *
     * 这个方法的返回映射应该总是返回bean的名称,对应bean的实例尽可能是配置中定义的顺序.
     *
     * @param type the class or interface to match, or {@code null} for all concrete beans
     * @return a Map with the matching beans, containing the bean names as
     * keys and the corresponding bean instances as values
     * @throws BeansException if a bean could not be created
     * @since 1.1.2
     * @see FactoryBean#getObjectType
     * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class)
     */
    <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException;

    /**
     * Return the bean instances that match the given object type (including
     * subclasses), judging from either bean definitions or the value of
     * {@code getObjectType} in the case of FactoryBeans.
     * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
     * check nested beans which might match the specified type as well.
     * <p>Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set,
     * which means that FactoryBeans will get initialized. If the object created by the
     * FactoryBean doesn't match, the raw FactoryBean itself will be matched against the
     * type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked
     * (which doesn't require initialization of each FactoryBean).
     * <p>Does not consider any hierarchy this factory may participate in.
     * Use BeanFactoryUtils' {@code beansOfTypeIncludingAncestors}
     * to include beans in ancestor factories too.
     * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
     * by other means than bean definitions.
     * <p>The Map returned by this method should always return bean names and
     * corresponding bean instances <i>in the order of definition</i> in the
     * backend configuration, as far as possible.
     *
     *
     * 返回匹配给定类型的bean的名称(包括子类),会从bean的定义或者FactoryBeans的{@code getObjectType}
     * 方法来判断,如果设置了allowEagerInit标志,则考虑由FactoryBeans创建的对象,这意味着FactoryBeans将会被初始化,
     * 如果由FactoryBeans创建的对象不匹配,那么原始FactoryBeans对象本身将与之匹配,如果allowEagerInit
     * 没有设置,那么智慧检查原始的FactoryBeans(因为不需要每个FactoryBeans初始化,就是没有提前初始化)
     * 当前方法不考虑这个工厂的父工厂,可以使用BeanFactoryUtils的{@code beanNamesForTypeIncludingAncestors}
     * 方法将会包括祖先的bean,不会忽略由其他方式而不是通过beanDefinitions定义的bean
     * 这个方法的返回映射应该总是返回bean的名称,对应bean的实例尽可能是配置中定义的顺序.
     *
     *
     * @param type the class or interface to match, or {@code null} for all concrete beans
     * @param includeNonSingletons whether to include prototype or scoped beans too
     * or just singletons (also applies to FactoryBeans)
     * @param allowEagerInit whether to initialize <i>lazy-init singletons</i> and
     * <i>objects created by FactoryBeans</i> (or by factory methods with a
     * "factory-bean" reference) for the type check. Note that FactoryBeans need to be
     * eagerly initialized to determine their type: So be aware that passing in "true"
     * for this flag will initialize FactoryBeans and "factory-bean" references.
     * @return a Map with the matching beans, containing the bean names as
     * keys and the corresponding bean instances as values
     * @throws BeansException if a bean could not be created
     * @see FactoryBean#getObjectType
     * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
     */
    <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
            throws BeansException;

    /**
     * Find all beans whose {@code Class} has the supplied {@link java.lang.annotation.Annotation} type.
     *
     * 查询所有拥有注解的bean的类
     *
     * @param annotationType the type of annotation to look for
     * @return a Map with the matching beans, containing the bean names as
     * keys and the corresponding bean instances as values
     * @throws BeansException if a bean could not be created
     */
    Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
            throws BeansException;

    /**
     * Find a {@link Annotation} of {@code annotationType} on the specified
     * bean, traversing its interfaces and super classes if no annotation can be
     * found on the given class itself.
     *
     * 在特定bean上查找注解,如果在本类上查询不到,那么就在接口或者超类中查询
     *
     * @param beanName the name of the bean to look for annotations on
     * @param annotationType the annotation class to look for
     * @return the annotation of the given type found, or {@code null}
     */
    <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType);

}
