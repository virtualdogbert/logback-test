package com.logback.test

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder

/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */

import ch.qos.logback.classic.gaffer.ConfigurationDelegate
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil
import ch.qos.logback.core.status.OnConsoleStatusListener
import ch.qos.logback.core.util.ContextUtil
import ch.qos.logback.core.util.OptionHelper
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.control.customizers.SecureASTCustomizer

import static org.codehaus.groovy.syntax.Types.*

class GafferConfigurator {

    LoggerContext context

    static final String DEBUG_SYSTEM_PROPERTY_KEY = "logback.debug";

    GafferConfigurator(LoggerContext context) {
        this.context = context
    }

    protected void informContextOfURLUsedForConfiguration(URL url) {
        ConfigurationWatchListUtil.setMainWatchURL(context, url);
    }

    void run(URL url) {
        informContextOfURLUsedForConfiguration(url);
        run(url.text);
    }

    void run(File file) {
        informContextOfURLUsedForConfiguration(file.toURI().toURL());
        run(file.text);
    }

    void run(String dslText) {
        Binding binding = new Binding();
        binding.setProperty("hostname", ContextUtil.localHostName);

        // Define SecureASTCustomizer to limit allowed
        // language syntax in scripts.
        final SecureASTCustomizer astCustomizer = new SecureASTCustomizer(
                // Do not allow method creation.
                methodDefinitionAllowed: false,

                // Do not allow closure creation.
                closuresAllowed: true,

                // No package allowed.
                packageAllowed: false,

                importsWhitelist: [
                        'java.lang.Object',
                        'org.springframework.beans.factory.annotation.Autowired',
                        'java.nio.charset.Charset.forName',
                        'ch.qos.logback.classic.AsyncAppender',
                        'ch.qos.logback.classic.Level',
                        'ch.qos.logback.classic.encoder.PatternLayoutEncoder',
                        'com.logentries.logback.LogentriesAppender',
                        'grails.util.BuildSettings',
                        'grails.util.Environment',
                        'org.slf4j.MDC',
                        'org.springframework.boot.logging.logback.ColorConverter',
                        'org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter',
                        'java.nio.charset.Charset',
                        'java.nio.charset.StandardCharsets'
                ],

                staticImportsWhitelist: ['java.nio.charset.Charset.forName'],
                // or staticImportBlacklist
                staticStarImportsWhitelist: [
                        'java.lang.Object',
                        'grails.util.Environment',
                        'ch.qos.logback.classic',
                        'ch.qos.logback.classic.Level',
                        'ch.qos.logback.core'
                ],
                // or staticStarImportsBlacklist

                // Make sure indirect imports are restricted.
                indirectImportCheckEnabled: true,

                // Only allow plus and minus tokens.
                tokensWhitelist: [
                        DIVIDE, PLUS, MINUS,
                        MULTIPLY, MOD, POWER,
                        PLUS_PLUS, MINUS_MINUS,
                        PLUS_EQUAL, LOGICAL_AND, COMPARE_EQUAL,
                        COMPARE_NOT_EQUAL, COMPARE_LESS_THAN, COMPARE_LESS_THAN_EQUAL,
                        LOGICAL_OR, NOT, COMPARE_GREATER_THAN, COMPARE_GREATER_THAN_EQUAL,
                        EQUALS, COMPARE_NOT_EQUAL, COMPARE_EQUAL
                ],


                // Disallow constant types.
                constantTypesClassesWhiteList: [Object, Integer, Float, Long, Double, BigDecimal, String, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE, Boolean.TYPE],
                // or constantTypesWhiteList
                // or constantTypesBlackList
                // or constantTypesClassesBlackList

                // Restrict method calls to whitelisted classes.
                // receiversClassesWhiteList: [],
                // or receiversWhiteList
                // or receiversClassesBlackList
                // or receiversBlackList

                // Ignore certain language statement by
                // whitelisting or blacklisting them.
                statementsBlacklist: [],
                // or statementsWhitelist

                // Ignore certain language expressions by
                // whitelisting or blacklisting them.
                //expressionsBlacklist: [MethodCallExpression]
                // or expresionsWhitelist
        )

        astCustomizer.addExpressionCheckers(new ScriptExpressionChecker())

        def configuration = new CompilerConfiguration()
        configuration.addCompilationCustomizers(importCustomizer(), astCustomizer)

        String debugAttrib = System.getProperty(DEBUG_SYSTEM_PROPERTY_KEY);
        if (OptionHelper.isEmpty(debugAttrib) || debugAttrib.equalsIgnoreCase("false")
                || debugAttrib.equalsIgnoreCase("null")) {
            // For now, Groovy/Gaffer configuration DSL does not support "debug" attribute. But in order to keep
            // the conditional logic identical to that in XML/Joran, we have this empty block.
        } else {
            OnConsoleStatusListener.addNewInstanceToContext(context);
        }

        // caller data should take into account groovy frames
        new ContextUtil(context).addGroovyPackages(context.getFrameworkPackages());

        Script dslScript = new GroovyShell(binding, configuration).parse(dslText)

        dslScript.metaClass.mixin(ConfigurationDelegate)
        dslScript.setContext(context)
        dslScript.metaClass.getDeclaredOrigin = { dslScript }

        dslScript.run()
    }

    protected ImportCustomizer importCustomizer() {
        def customizer = new ImportCustomizer()


        def core = 'ch.qos.logback.core'
        customizer.addStarImports(core, "${core}.encoder", "${core}.read", "${core}.rolling", "${core}.status",
                                  "ch.qos.logback.classic.net")

        customizer.addImports(PatternLayoutEncoder.class.name)

        customizer.addStaticStars(Level.class.name)

        customizer.addStaticImport('off', Level.class.name, 'OFF')
        customizer.addStaticImport('error', Level.class.name, 'ERROR')
        customizer.addStaticImport('warn', Level.class.name, 'WARN')
        customizer.addStaticImport('info', Level.class.name, 'INFO')
        customizer.addStaticImport('debug', Level.class.name, 'DEBUG')
        customizer.addStaticImport('trace', Level.class.name, 'TRACE')
        customizer.addStaticImport('all', Level.class.name, 'ALL')

        customizer
    }

}