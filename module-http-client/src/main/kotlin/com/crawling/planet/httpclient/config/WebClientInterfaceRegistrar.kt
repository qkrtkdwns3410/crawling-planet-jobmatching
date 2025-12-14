package com.crawling.planet.httpclient.config

import com.crawling.planet.httpclient.annotation.EnableWebClients
import com.crawling.planet.httpclient.annotation.WebClientInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.EnvironmentAware
import org.springframework.context.ResourceLoaderAware
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.core.type.AnnotationMetadata
import org.springframework.core.type.filter.AnnotationTypeFilter

private val logger = KotlinLogging.logger {}

/**
 * WebClient 인터페이스 자동 등록
 * @EnableWebClients 어노테이션이 붙은 패키지를 스캔하여 @WebClientInterface 인터페이스를 Bean으로 등록
 */
class WebClientInterfaceRegistrar : ImportBeanDefinitionRegistrar, EnvironmentAware, ResourceLoaderAware {
    
    private lateinit var environment: Environment
    private lateinit var resourceLoader: ResourceLoader
    
    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }
    
    override fun setResourceLoader(resourceLoader: ResourceLoader) {
        this.resourceLoader = resourceLoader
    }
    
    override fun registerBeanDefinitions(
        importingClassMetadata: AnnotationMetadata,
        registry: BeanDefinitionRegistry
    ) {
        val attributes = importingClassMetadata
            .getAnnotationAttributes(EnableWebClients::class.java.name)
            ?: return
        
        val basePackages = getBasePackages(importingClassMetadata, attributes)
        
        logger.info { "Scanning WebClient interfaces in packages: $basePackages" }
        
        val scanner = createScanner()
        
        for (basePackage in basePackages) {
            val candidates = scanner.findCandidateComponents(basePackage)
            
            for (candidate in candidates) {
                if (candidate is AnnotatedBeanDefinition) {
                    registerWebClientInterface(candidate, registry)
                }
            }
        }
    }
    
    /**
     * 스캔할 패키지 목록 결정
     */
    private fun getBasePackages(
        importingClassMetadata: AnnotationMetadata,
        attributes: Map<String, Any>
    ): Set<String> {
        val packages = mutableSetOf<String>()
        
        @Suppress("UNCHECKED_CAST")
        val basePackages = attributes["basePackages"] as? Array<String>
        if (!basePackages.isNullOrEmpty()) {
            packages.addAll(basePackages)
        }
        
        @Suppress("UNCHECKED_CAST")
        val basePackageClasses = attributes["basePackageClasses"] as? Array<Class<*>>
        basePackageClasses?.forEach { clazz ->
            packages.add(clazz.packageName)
        }
        
        // 기본값: @EnableWebClients가 선언된 클래스의 패키지
        if (packages.isEmpty()) {
            packages.add(getPackageName(importingClassMetadata.className))
        }
        
        return packages
    }
    
    private fun getPackageName(className: String): String {
        val lastDotIndex = className.lastIndexOf('.')
        return if (lastDotIndex != -1) className.substring(0, lastDotIndex) else ""
    }
    
    /**
     * ClassPath 스캐너 생성
     */
    private fun createScanner(): ClassPathScanningCandidateComponentProvider {
        return object : ClassPathScanningCandidateComponentProvider(false, environment) {
            override fun isCandidateComponent(beanDefinition: AnnotatedBeanDefinition): Boolean {
                // 인터페이스만 허용
                return beanDefinition.metadata.isInterface && !beanDefinition.metadata.isAnnotation
            }
        }.apply {
            resourceLoader = this@WebClientInterfaceRegistrar.resourceLoader
            addIncludeFilter(AnnotationTypeFilter(WebClientInterface::class.java))
        }
    }
    
    /**
     * WebClient 인터페이스 Bean 등록
     */
    private fun registerWebClientInterface(
        candidate: AnnotatedBeanDefinition,
        registry: BeanDefinitionRegistry
    ) {
        val interfaceClassName = candidate.beanClassName ?: return
        val interfaceClass = Class.forName(interfaceClassName)
        
        val annotation = interfaceClass.getAnnotation(WebClientInterface::class.java)
        val beanName = annotation.name.ifEmpty { 
            interfaceClass.simpleName.replaceFirstChar { it.lowercase() }
        }
        
        logger.info { "Registering WebClient interface: $interfaceClassName as '$beanName'" }
        
        val beanDefinition = BeanDefinitionBuilder
            .genericBeanDefinition(WebClientInterfaceFactoryBean::class.java)
            .addConstructorArgValue(interfaceClass)
            .setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE)
            .setLazyInit(true)
            .beanDefinition
        
        registry.registerBeanDefinition(beanName, beanDefinition)
    }
}

