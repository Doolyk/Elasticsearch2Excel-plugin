package org.esreport;

import org.elasticsearch.common.inject.AbstractModule;

public class ESReportPluginRestModule extends AbstractModule {

    protected void configure() {
        bind(ESReportPluginRestHandler.class).asEagerSingleton();
    }
}