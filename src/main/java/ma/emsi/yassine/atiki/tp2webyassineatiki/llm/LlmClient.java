package ma.emsi.yassine.atiki.tp2webyassineatiki.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.enterprise.context.Dependent;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.List;

/** Client métier propre à une vue JSF et à sa conversation. */
@Dependent
public class LlmClient implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String systemRole;

    // Le proxy IA, le modèle et la mémoire LangChain4j ne sont pas sérialisables.
    private transient Assistant assistant;
    private transient ChatMemory chatMemory;

    // Seuls les messages sont conservés à la passivation, jamais la clé de l'API.
    private String messagesJson = "[]";

    public LlmClient() {
        initialiser(creerModele());
    }

    // Permet aussi de vérifier le métier avec un modèle local sans appel réseau.
    LlmClient(ChatModel model) {
        initialiser(model);
    }

    private static ChatModel creerModele() {
        String apiKey = System.getenv("GEMINI_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("La variable d'environnement GEMINI_KEY est absente.");
        }
        return GoogleGenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-3.5-flash-lite")
                .timeout(Duration.ofSeconds(60))
                .maxRetries(0)
                .logRequestsAndResponses(false)
                .build();
    }

    // Reconstruction également utilisée après une désérialisation de la vue.
    synchronized void initialiser(ChatModel model) {
        this.chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        for (ChatMessage message : ChatMessageDeserializer.messagesFromJson(messagesJson)) {
            this.chatMemory.add(message);
        }
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .build();
    }

    private void verifierInitialisation() {
        if (assistant == null) {
            initialiser(creerModele());
        }
    }

    /** Tout changement de rôle commence une conversation sans ancien contexte. */
    public synchronized void setSystemRole(String systemRole) {
        if (systemRole == null || systemRole.isBlank()) {
            throw new IllegalArgumentException("Le rôle système est obligatoire.");
        }
        verifierInitialisation();
        this.systemRole = systemRole;
        chatMemory.clear();
        chatMemory.add(SystemMessage.from(systemRole));
        memoriserMessages();
    }

    public synchronized String chat(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("La question est vide.");
        }
        if (systemRole == null) {
            throw new IllegalStateException("Choisissez un rôle avant de commencer la conversation.");
        }
        verifierInitialisation();
        List<ChatMessage> avantAppel = List.copyOf(chatMemory.messages());
        try {
            String reponse = assistant.chat(prompt);
            memoriserMessages();
            return reponse;
        } catch (RuntimeException erreur) {
            // Une requête échouée ne doit pas laisser une question isolée en mémoire.
            chatMemory.clear();
            avantAppel.forEach(chatMemory::add);
            // Les exceptions du SDK peuvent contenir des détails de requête sensibles.
            throw new IllegalStateException("Gemini n'a pas pu répondre. Réessayez dans un instant.");
        }
    }

    private void memoriserMessages() {
        messagesJson = ChatMessageSerializer.messagesToJson(chatMemory.messages());
    }

    @Serial
    private synchronized void writeObject(ObjectOutputStream sortie) throws IOException {
        if (chatMemory != null) {
            memoriserMessages();
        }
        sortie.defaultWriteObject();
    }
}
