package com.crawling.planet.httpclient.config

import com.crawling.planet.httpclient.core.WebClientProxyFactory
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment
import org.springframework.web.reactive.function.client.WebClient
import kotlin.reflect.KClass

/**
 * WebClient 인터페이스 프록시를 생성하는 FactoryBean
 */
class WebClientInterfaceFactoryBean<T : Any>(
    private val interfaceClass: Class<T>
) : FactoryBean<T>, BeanFactoryAware, EnvironmentAware, InitializingBean {
    
    private lateinit var beanFactory: BeanFactory
    private lateinit var environment: Environment
    private var proxyInstance: T? = null
    
    override fun setBeanFactory(beanFactory: BeanFactory) {
        this.beanFactory = beanFactory
    }
    
    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }
    
    override fun afterPropertiesSet() {
        // 프록시 생성은 getObject()에서 lazy하게 수행
    }
    
    override fun getObject(): T {
        if (proxyInstance == null) {
            val webClientBuilder = beanFactory.getBean(WebClient.Builder::class.java)
            
            val factory = WebClientProxyFactory(
                webClientBuilder = webClientBuilder,
                environment = environment,
                beanFactory = beanFactory
            )
            
            @Suppress("UNCHECKED_CAST")
            proxyInstance = factory.create(interfaceClass.kotlin as KClass<T>)
        }
        return proxyInstance!!
    }
    
    override fun getObjectType(): Class<T> = interfaceClass
    
    override fun isSingleton(): Boolean = true
}

