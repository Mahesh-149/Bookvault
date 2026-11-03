package org.example.bookvault;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF -> chunks -> embeddings -> Pinecone, then summarize / answer with an LLM.
 * The VectorStore bean is the Pinecone store configured in application.properties.
 */
@Service
public class BookRagService {

    private static final int PINECONE_DELETE_BATCH = 1000;
    private static final int INDEX_WAIT_ATTEMPTS = 8;
    private static final long INDEX_WAIT_MS = 1500;

    private static final String SUMMARY_SYSTEM = """
            You summarize books and documents. Use ONLY the excerpts provided.
            Write: (1) a 3-4 sentence overview, (2) 5-8 key points as bullets,
            (3) who the document is useful for. If the excerpts are too thin, say so.
            """;

    private static final String ANSWER_SYSTEM = """
            Answer the question using ONLY the excerpts provided.
            If the answer is not in the excerpts, say you could not find it in this document.
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RagProperties props;

    public BookRagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder, RagProperties props) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.props = props;
    }

    /** Reads the PDF, splits it into chunks, embeds them and upserts the vectors into Pinecone. Returns the bookId. */
    public String ingest(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "untitled.pdf" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files can be indexed right now.");
        }

        String bookId = UUID.randomUUID().toString();

        PagePdfDocumentReader reader = new PagePdfDocumentReader(new ByteArrayResource(file.getBytes()));
        List<Document> pages = reader.get();

        // chunkSize tokens, min chunk chars, min length to embed, max chunks, keep separators
        TokenTextSplitter splitter = new TokenTextSplitter(
                props.getChunkSize(), 350, 5, 10000, true, List.of('.', '?', '!', '\n'));
        List<Document> chunks = splitter.apply(pages);

        // Deterministic ids (bookId-0, bookId-1, ...) so the book can be deleted later by id.
        // Pinecone serverless indexes cannot delete by metadata filter.
        int total = chunks.size();
        List<Document> toStore = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            Document c = chunks.get(i);
            Map<String, Object> meta = new HashMap<>(c.getMetadata());
            meta.put("bookId", bookId);
            meta.put("fileName", name);
            meta.put("chunkIndex", i);
            meta.put("totalChunks", total);
            toStore.add(new Document(bookId + "-" + i, c.getText(), meta));
        }

        // Upsert in batches to stay under Pinecone request size limits.
        for (int i = 0; i < toStore.size(); i += 100) {
            vectorStore.add(toStore.subList(i, Math.min(i + 100, toStore.size())));
        }
        return bookId;
    }

    /** Retrieves the most representative chunks of one book and asks the LLM to summarize them. */
    public String summarize(String bookId) {
        List<Document> docs = searchWithRetry(bookId,
                "main ideas, key arguments, conclusions and overall purpose of this document",
                props.getSummaryTopK());
        requireContent(docs);
        return chatClient.prompt()
                .system(SUMMARY_SYSTEM)
                .user("Excerpts:\n" + join(docs) + "\n\nSummarize this document.")
                .call()
                .content();
    }

    /** Answers a question about one book using only its most similar chunks. */
    public String ask(String bookId, String question) {
        List<Document> docs = searchWithRetry(bookId, question, props.getAnswerTopK());
        requireContent(docs);
        return chatClient.prompt()
                .system(ANSWER_SYSTEM)
                .user("Excerpts:\n" + join(docs) + "\n\nQuestion: " + question)
                .call()
                .content();
    }

    /** Removes every vector of a book. Call this when the user deletes the book. */
    public void delete(String bookId) {
        List<Document> probe = search(bookId, "document", 1);
        if (probe.isEmpty()) {
            return;
        }
        Object t = probe.get(0).getMetadata().get("totalChunks");
        int total = (t instanceof Number n) ? n.intValue() : 0;
        List<String> ids = IntStream.range(0, total).mapToObj(i -> bookId + "-" + i).collect(Collectors.toList());
        for (int i = 0; i < ids.size(); i += PINECONE_DELETE_BATCH) {
            vectorStore.delete(ids.subList(i, Math.min(i + PINECONE_DELETE_BATCH, ids.size())));
        }
    }

    private List<Document> search(String bookId, String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(props.getSimilarityThreshold())
                .filterExpression(new FilterExpressionBuilder().eq("bookId", bookId).build())
                .build();
        List<Document> result = vectorStore.similaritySearch(request);
        return result == null ? List.of() : result;
    }

    /**
     * Pinecone is eventually consistent: vectors upserted a moment ago can take a few
     * seconds to become searchable. Retry briefly so the summary right after upload works.
     */
    private List<Document> searchWithRetry(String bookId, String query, int topK) {
        for (int attempt = 0; attempt < INDEX_WAIT_ATTEMPTS; attempt++) {
            List<Document> result = search(bookId, query, topK);
            if (!result.isEmpty()) {
                return result;
            }
            try {
                Thread.sleep(INDEX_WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return List.of();
    }

    private void requireContent(List<Document> docs) {
        if (docs.isEmpty()) {
            throw new IllegalArgumentException("No indexed content found for this bookId.");
        }
    }

    private String join(List<Document> docs) {
        return docs.stream()
                .map(d -> "---\n" + d.getText())
                .collect(Collectors.joining("\n"));
    }
}