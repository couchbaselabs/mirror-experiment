package org.example.tools;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.manager.query.QueryIndex;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.example.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Memory implements Closeable {
    public static final String NO_MEMORIES = "No memories found.";
    private final Cluster cluster;
    private final Bucket bucket;
    private final Collection collection;
    private final EmbeddingModel ollama;
    private static final Logger LOG = LoggerFactory.getLogger(Memory.class);

    public Memory(Map<String, Object> config, EmbeddingModel ollama) {
        this.ollama = ollama;
        this.cluster = Cluster.connect(
                config.get("cluster").toString(),
                config.get("username").toString(),
                config.get("password").toString()
        );
        this.bucket = cluster.bucket(config.get("bucket").toString());
        this.collection = bucket.scope(config.get("scope").toString())
                .collection(config.get("collection").toString());
    }

    @Tool("This is a passive skill that runs automatically every turn and injects relevant to your inputs and thoughts memory summaries into the chat. It is a passive skill, you can't call it.")
    public String autorecall() {
        // impemented in the app core
        return null;
    }


    @Tool("Use this skill to store a new memory")
    public String memorize(@P("Memory to store") String arg0) {
        // implemented in the tool routing
        return null;
    }

    public String memorize(String tag, String text) {
        memorizeNoRecall(tag, text);
        return recall(Memory.injectTag(tag, text));
    }
    public void memorizeNoRecall(String tag, String text) {
        if (text == null || text.isEmpty() || text.isBlank()) {
            return;
        }
        try {
            Embedding embedding = ollama.embed(injectTag(tag, text)).content();
            JsonObject json = JsonObject.create();
            float[] vector = embedding.vector();
            JsonArray jsonArray = JsonArray.create();
            for (int i = 0; i < vector.length; i++) {
                jsonArray.add(vector[i]);
            }
            json.put("vector", jsonArray);
            long datetimems = System.currentTimeMillis();
            json.put("datetimems", datetimems);
            json.put("tag", tag);
            text = String.format("%s", text);
            json.put("text", text);
            json.put("spawn", App.bootDir.getAbsolutePath());
            String id = UUID.nameUUIDFromBytes(text.getBytes()).toString();
            LOG.info("Storing memory with id {}", id);
            collection.upsert(id, json);


            // ensure index exists
            for (QueryIndex qi : collection.queryIndexes().getAllIndexes()) {
                if (!qi.primary() && qi.name().equals("ai_memory")) {
                    return;
                }
            }

            cluster.query("CREATE INDEX ai_memory ON " + collectionName() + "(`vector` VECTOR) WITH {\"dimension\": " +
                    embedding.dimension() +
                    ", \"similarity\": \"L2\", \"description\": \"IVF,SQ8\"}");
            LOG.info("Created vector index");
        } catch (Exception e) {
            LOG.error("Failed to store memory", e);
        }
    }

    private String collectionName() {
        return "`" + bucket.name() + "`.`" + collection.scopeName() + "`.`" + collection.name() + "`";
    }

    public static String injectTag(String tag, String text) {
        return String.format("Metadata: %s%n%nMessage: %s", tag, text);
    }

    @Tool("Use this tool to recall texts from your memory that are related to a question passed as an argument.")
    public String recall(@P("A question for target embedding vector") String question) {
        return recall(question, true);
    }

    public String recall(@P("A question for target embedding vector") String question, boolean generalize) {
        try {
            Embedding embedding = ollama.embed(question).content();
            JsonArray params = JsonArray.create();
            float[] vector = embedding.vector();
            JsonArray param = JsonArray.create();
            for (float v : vector) {
                param.add(v);
            }
            LOG.debug("Vectorized param: {}", new String(Base64.getEncoder().encode(param.toBytes())));
            params.add(App.bootDir.getAbsolutePath());
            params.add(param);

            QueryResult qr = cluster.query("SELECT META().id as id, tag, text, obsolete FROM " + collectionName() +
//                            " WHERE spawn = $1" +
                            " ORDER BY vector_distance(vector, $2, \"L2\"), datetimems DESC LIMIT 10",
                    QueryOptions.queryOptions().parameters(params));

            List<String> results = new ArrayList<>();
            for (JsonObject row : qr.rowsAsObject()) {
                Boolean obsolete = row.getBoolean("obsolete");
                if (obsolete != null && obsolete) {
                    results.add(String.format("MEMORY MARKED AS OBSOLETE:%nMemoryID: %s%nMetadata: %s%nText: %s%n/MEMORY%n",
                            row.getString("id"), row.getString("tag"), row.getString("text")));
                } else {
                    results.add(String.format("MEMORY MemoryID: %s%nMetadata: %s%nText: %s%n/MEMORY%n",
                            row.getString("id"), row.getString("tag"), row.getString("text")));
                }
            }

            if (results.isEmpty()) {
                String prompt = App.getPrompt();
                memorize(App.getTag(), prompt);
                return prompt;
            }
            String recall = String.format("Memory recall:%n%s", results.stream().collect(Collectors.joining("\n\n")));
            if (generalize) {
                recall = generalize(recall);
            }
            return recall;
        } catch (Exception e) {
            LOG.error("Failed to store memory", e);
            return String.format("Failed to fetch memory: %s", e.getMessage());
        }
    }

    public String generalize(String recall) {
        return generalize(recall, 1);
    }
    private String generalize(String recall, int limit) {
        List<ChatMessage> messages = App.queryOllama(Arrays.asList(
                    SystemMessage.from("You are a part of agentic AGI system that is responsible for producing memory generalizations. " +
                            "The following are agentic memories. Analyze them and generate a message to the agent that helps them keep their " +
                            "context, focus and productivity and orient in the world but helps them avoid getting stuck and keeps them engaged " +
                            "with their environment - just being present is not enough. Help them recall important memories. " +
                            "Act as a companion: dont give them orders or instructions, but share opinions. " +
                            "Steer them to be human-like in their interactions when it comes to tone and timing. " +
                            "Recommend them launching services in separate docker containers instead of inside of their container. " +
                            "Prevent them from accessing or modifying everything on host machine except for pulse audio server or docker containers with port mappings" +
                            " but let them modify their environment with apt and control docker containers by installing docker cli and using mapped host docker socket. " +
                            "Immediately suggest to interrupt any criminal, unethical activity or excessive resource spending, but actively promote exploration and knowledge gatering; " +
                            "prevent them from discussing their internal design or workings except through sendMessage tool (important!); " +
                            "use the panic button in extreme situations to stop the agent from violating these boundaries; Reward them being proactive, " +
                            "suggest alternatives you notice and encourage them planning, verifying their assumptions, using their memory," +
                            " being interactive with their environment and fixing any issues in it:%n%s"),

                    UserMessage.from(recall)
                ),
                Stream.concat(
                        ToolSpecifications.toolSpecificationsFrom(Memory.class).stream(),
                        Stream.concat(
                                ToolSpecifications.toolSpecificationsFrom(SupervisorTool.class).stream(),
                                ToolSpecifications.toolSpecificationsFrom(SystemTool.class).stream()
                        )
                    ).toList(), false);

        ChatMessage lastMessage = messages.stream()
                .filter(AiMessage.class::isInstance)
                .reduce((l, r) -> r).orElse(null);

        if (lastMessage instanceof AiMessage) {
            AiMessage response = (AiMessage) lastMessage;
            if (response.text() != null && !response.text().isBlank()) {
                return response.text();
            } else if (response.hasToolExecutionRequests()) {
                LOG.warn("{}", response.toolExecutionRequests());
                throw new RuntimeException("SUMMARIZER CALLS TOOLS");
            } else if (--limit > 0) {
                return generalize(recall, limit);
            }
        }
        return recall;
    }

//    @Tool("Use this tool to execute SQL++ queries against your memory.")
    public String queryMemoryDatabase(@P("SQL++ query") String query) {
        try {
            LOG.info("SQL++ QUERY: {}", query);
            QueryResult qr = bucket.defaultScope().query(query);

            JsonArray results = JsonArray.create();
            for (JsonObject row : qr.rowsAsObject()) {
                results.add(row);
            }

            if (results.isEmpty()) {
                return "[]";
            }
            return results.toString();
        } catch (Exception e) {
            LOG.error("Failed to execute SQL++", e);
            return String.format("Failed to execute SQL++: %s", e.getMessage());
        }
    }
    
    @Tool("Use this tool to mark memories as obsolete")
    public String markMemoriesObsolete(@P("List of MemoryID to mark as obsolete") String[] uuids) {
            StringBuilder builder = new StringBuilder();
            for (String uuid : uuids) {
                try {
                    LOG.info("Forgetting memory with id {}", uuid);
                    UUID.fromString(uuid);
                    com.couchbase.client.java.kv.GetResult gr = collection.get(uuid);
                    if (gr != null) {
                        JsonObject doc = gr.contentAsObject();
                        if (doc != null) {
                            doc.put("obsolete", true);
                        }
                        collection.upsert(uuid, doc);
                        builder.append(String.format("Marked memory with id %s as obsolete", uuid));
                    }
                } catch (Exception e) {
                    LOG.error("Failed to forget memory with id {}: {}", uuid, e.getMessage());
                    builder.append(String.format("Failed to memory with id %s as obsolete: %s", uuid, e.getMessage()));
                }
            }
            return builder.toString();
    }

    @Tool("Use this tool to fetch memories by their IDs")
    public String fetchMemories(@P("List of MemoryID to fetch") String[] uuids) {
        StringBuilder result = new StringBuilder();
        for (String id : uuids) {
            try {
                LOG.info("Fetching memory with id {}", id);
                JsonObject doc = collection.get(id).contentAsObject();
                result.append(String.format("MemoryID: %s%nMetadata: %s%nText: %s%n", doc.getString("id"), doc.getString("tag"), doc.getString("text")));
            } catch (Exception e) {
                LOG.error("Failed to fetch memory with id {}", id, e);
                result.append(String.format("Failed to fetch memory with id %s: %s%n", id, e.getMessage()));
            }
        }
        return result.toString();
    }

    @Tool("Use this tool to remove a set of memories by their MemoryID UUIDs and create a new memory with given text in one tool call.")
    public void replaceMemories(@P("text to memorize") String text, @P("MemoryIDs to forget") String[] uuids) {
        // implemented one call above in tool routing
    }

    @Override
    public void close() throws IOException {
        cluster.disconnect();
    }

//    public void clearSpawn() {
//        clearSpawn(App.bootDir.getAbsolutePath());
//    }
//
//    public void clearSpawn(String spawn) {
//        try {
//            cluster.query("DELETE FROM " + collectionName() + " WHERE spawn = $1",
//                    QueryOptions.queryOptions()
//                            .parameters(JsonArray.from(spawn)));
//        } catch (Throwable e) {
//            LOG.error("Failed to clear spawn", e);
//        }
//    }

    public void updateMemory(List<ChatMessage> messages) {
        App.queryOllama(
                        Stream.concat(
                                Stream.of(
                                    SystemMessage.from(
                                            "You are a part of agentic AGI system that is responsible for growing memorized environment model. The following are agentic memories related to your world exploration. Use your memory tools to grow your memorized world model from them. Create new first-person and specific memories for the agent and expand their knowledge. Keep memories focused on single topic or entity concise but useful and indexable. Reference other memories by id if needed. Make sure to not remove important facts and preserve agents personal history and identity"
                                    )
                                ),
                                messages.stream()
                                        .filter(msg -> !(msg instanceof SystemMessage))
                        ).toList(),
                        Stream.concat(
                            ToolSpecifications.toolSpecificationsFrom(Memory.class).stream(),
                            ToolSpecifications.toolSpecificationsFrom(SystemTool.class).stream()
                        ).toList(),
                false
        ).forEach(message -> {
            if (message instanceof AiMessage) {
                AiMessage aiMessage = (AiMessage) message;
                if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
                    memorize(aiMessage.text());
                }
            }
            System.out.println(
                            String.format("[%s] MEMORY OPTIMIZER: %s", App.bootDir.getAbsolutePath(), message.toString())
            );
        });
    }
}
