package com.cleanbuild.tech.monolit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@EnableJpaRepositories(basePackages = ["com.cleanbuild.tech.monolit.repository"])
@EntityScan(basePackages = ["com.cleanbuild.tech.monolit.model"])
@SpringBootApplication
open class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
