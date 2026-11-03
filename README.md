@'
# BookVault 📚⚡

A Spring Boot & Spring AI RAG (Retrieval-Augmented Generation) microservice that ingests PDF documents, splits and indexes chunks into Pinecone vector storage, and provides endpoints for document summarization and targeted Q&A via LLMs.

---

## Architecture & Features

- **Document Ingestion:** Reads uploaded PDFs via `PagePdfDocumentReader` and splits text into semantic chunks using `TokenTextSplitter`.
- **Pinecone Vector Store:** Chunks are tagged with metadata (`bookId`, `fileName`, `chunkIndex`, `totalChunks`) and stored using deterministic IDs (`{bookId}-{index}`) to allow deletion on Pinecone serverless indexes.
- **Automated Summarization:** Generates high-level overviews and key takeaways using Spring AI `ChatClient`.
- **Grounded Q&A:** Answers user questions strictly using relevant document excerpts retrieved via vector similarity search.
- **Eventual Consistency Handling:** Built-in polling retry mechanism to handle Pinecone index propagation delays right after upload.

---

## Tech Stack

- **Java 17+** / **Java 21**
- **Spring Boot 3.x**
- **Spring AI** (`spring-ai-starter-model`, `spring-ai-pinecone-store-vector`)
- **Pinecone** (Vector Database)
- **Maven** or **Gradle**

---

## Getting Started

### 1. Prerequisites

- Java 17 or higher installed
- An active [Pinecone](https://www.pinecone.io/) account, index, and API key
- An LLM API key (e.g., OpenAI, Anthropic, or Ollama configured for Spring AI)

### 2. Configuration

Add your Pinecone and AI credentials to `src/main/resources/application.properties` (or `application.yml`):

```properties
# Spring AI - LLM Setup (Example: OpenAI)
spring.ai.openai.api-key=your_openai_api_key

# Spring AI - Pinecone Configuration
spring.ai.vectorstore.pinecone.api-key=your_pinecone_api_key
spring.ai.vectorstore.pinecone.environment=your_pinecone_environment
spring.ai.vectorstore.pinecone.index-name=your_index_name
spring.ai.vectorstore.pinecone.namespace=default

# BookVault RAG Custom Properties
app.rag.chunk-size=800
app.rag.summary-top-k=20
app.rag.answer-top-k=6
app.rag.similarity-threshold=0.0
