package com.aechak.infra.s3

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(S3Properties::class)
class S3Config
