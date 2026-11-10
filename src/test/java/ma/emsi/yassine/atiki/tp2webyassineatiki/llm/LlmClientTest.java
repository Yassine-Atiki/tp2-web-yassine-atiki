package ma.emsi.yassine.atiki.tp2webyassineatiki.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Ces tests utilisent un modèle factice, pas Gemini. */
class LlmClientTest {
    private static final String TRADUCTEUR = "Translate French into English.";
    private static final String GUIDE = "Describe places to visit.";

    @Test
    void transmetLeRoleCommeSystemMessageEtConserveLesToursPrecedents() {
        ModeleLocal model = new ModeleLocal();
        LlmClient client = new LlmClient(model);
        client.setSystemRole(TRADUCTEUR);

        assertEquals("réponse locale 1", client.chat("bonjour"));
        assertEquals(List.of(SystemMessage.from(TRADUCTEUR), UserMessage.from("bonjour")),
                model.requetes.getFirst());

        client.chat("et au revoir ?");
        assertEquals(List.of(SystemMessage.from(TRADUCTEUR), UserMessage.from("bonjour"),
                AiMessage.from("réponse locale 1"), UserMessage.from("et au revoir ?")),
                model.requetes.getLast());
    }

    @Test
    void changementDeRoleEtNouveauChatEliminentToutAncienContexte() {
        ModeleLocal model = new ModeleLocal();
        LlmClient client = new LlmClient(model);
        client.setSystemRole(TRADUCTEUR);
        client.chat("bonjour");

        client.setSystemRole(GUIDE);
        client.chat("Casablanca");
        assertEquals(List.of(SystemMessage.from(GUIDE), UserMessage.from("Casablanca")),
                model.requetes.getLast());

        // Un nouveau chat doit aussi vider la mémoire quand on garde le même rôle.
        client.setSystemRole(GUIDE);
        client.chat("Rabat");
        assertEquals(List.of(SystemMessage.from(GUIDE), UserMessage.from("Rabat")),
                model.requetes.getLast());
    }

    @Test
    void gardeLeRoleEtLimiteLaMemoireAVingtMessages() throws Exception {
        ModeleLocal model = new ModeleLocal();
        LlmClient client = new LlmClient(model);
        client.setSystemRole(TRADUCTEUR);
        for (int tour = 1; tour <= 30; tour++) {
            client.chat("question " + tour);
        }
        var champMemoire = LlmClient.class.getDeclaredField("chatMemory");
        champMemoire.setAccessible(true);
        List<ChatMessage> messages = ((ChatMemory) champMemoire.get(client)).messages();
        assertEquals(20, messages.size());
        assertEquals(SystemMessage.from(TRADUCTEUR), messages.getFirst());
        assertEquals(1, messages.stream().filter(SystemMessage.class::isInstance).count());
        assertFalse(messages.contains(UserMessage.from("question 1")));
        assertEquals(AiMessage.from("réponse locale 30"), messages.getLast());
        assertTrue(messages.contains(UserMessage.from("question 30")));
    }

    @Test
    void serialiseLesMessagesEtReconstruitLeServiceSansSerialiserLeModele() throws Exception {
        ModeleLocal model = new ModeleLocal(); // volontairement non Serializable
        LlmClient client = new LlmClient(model);
        client.setSystemRole(TRADUCTEUR);
        client.chat("bonjour");

        byte[] donnees;
        try (ByteArrayOutputStream octets = new ByteArrayOutputStream();
             ObjectOutputStream sortie = new ObjectOutputStream(octets)) {
            sortie.writeObject(client);
            sortie.flush();
            donnees = octets.toByteArray();
        }
        assertFalse(new String(donnees, StandardCharsets.ISO_8859_1).contains("ModeleLocal"));

        LlmClient restaure;
        try (ObjectInputStream entree = new ObjectInputStream(new ByteArrayInputStream(donnees))) {
            restaure = (LlmClient) entree.readObject();
        }
        ModeleLocal nouveauModele = new ModeleLocal();
        // Même reconstruction qu'en production, avec un modèle sans réseau pour ce test.
        restaure.initialiser(nouveauModele);
        restaure.chat("merci");
        assertEquals(List.of(SystemMessage.from(TRADUCTEUR), UserMessage.from("bonjour"),
                AiMessage.from("réponse locale 1"), UserMessage.from("merci")),
                nouveauModele.requetes.getFirst());
    }

    @Test
    void uneErreurNePolluePasLaMemoireEtNExposePasLesDetailsDuFournisseur() {
        ModeleLocal model = new ModeleLocal();
        LlmClient client = new LlmClient(model);
        client.setSystemRole(TRADUCTEUR);
        client.chat("bonjour");
        model.echouer = true;

        IllegalStateException erreur = assertThrows(IllegalStateException.class,
                () -> client.chat("question échouée"));
        assertNull(erreur.getCause());
        assertFalse(erreur.getMessage().contains("detail sensible factice"));

        model.echouer = false;
        client.chat("merci");
        assertEquals(List.of(SystemMessage.from(TRADUCTEUR), UserMessage.from("bonjour"),
                AiMessage.from("réponse locale 1"), UserMessage.from("merci")),
                model.requetes.getLast());
    }

    private static final class ModeleLocal implements ChatModel {
        private final List<List<ChatMessage>> requetes = new ArrayList<>();
        private boolean echouer;

        @Override
        public ChatResponse doChat(ChatRequest requete) {
            requetes.add(List.copyOf(requete.messages()));
            if (echouer) {
                throw new IllegalStateException("detail sensible factice");
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("réponse locale " + requetes.size()))
                    .build();
        }
    }
}
