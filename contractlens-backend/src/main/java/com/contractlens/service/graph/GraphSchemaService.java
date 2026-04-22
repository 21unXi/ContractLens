package com.contractlens.service.graph;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

@Service
public class GraphSchemaService {

    private final Driver neo4jDriver;

    public GraphSchemaService(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    public void ensureConstraints() {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run("CREATE CONSTRAINT knowledge_doc_docId IF NOT EXISTS FOR (n:KnowledgeDoc) REQUIRE n.docId IS UNIQUE");
                tx.run("CREATE CONSTRAINT law_article_name IF NOT EXISTS FOR (n:LawArticle) REQUIRE n.name IS UNIQUE");
                tx.run("CREATE CONSTRAINT risk_type_name IF NOT EXISTS FOR (n:RiskType) REQUIRE n.name IS UNIQUE");
                tx.run("CREATE INDEX knowledge_doc_docType IF NOT EXISTS FOR (n:KnowledgeDoc) ON (n.docType)");
                return null;
            });
        }
    }
}

