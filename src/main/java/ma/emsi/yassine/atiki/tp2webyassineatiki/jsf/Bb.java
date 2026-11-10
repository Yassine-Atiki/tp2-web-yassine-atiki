package ma.emsi.yassine.atiki.tp2webyassineatiki.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ma.emsi.yassine.atiki.tp2webyassineatiki.llm.LlmClient;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Adaptation du backing bean du TP0 : les échanges sont délégués à LangChain4j. */
@Named
@ViewScoped
public class Bb implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String roleSysteme;
    private boolean roleSystemeChangeable = true;
    private List<SelectItem> listeRolesSysteme;
    private String question;
    private String reponse;
    private final StringBuilder conversation = new StringBuilder();

    @Inject
    private LlmClient llmClient;

    public Bb() {
        roleSysteme = (String) getRolesSysteme().getFirst().getValue();
    }

    public String envoyer() {
        if (question == null || question.isBlank()) {
            afficherErreur("Il manque le texte de la question.");
            return null;
        }
        if (roleSysteme == null || roleSysteme.isBlank()) {
            afficherErreur("Vous devez indiquer le rôle de l'API.");
            return null;
        }
        try {
            if (conversation.isEmpty()) {
                // Le rôle doit être dans la mémoire AVANT le premier message utilisateur.
                llmClient.setSystemRole(roleSysteme);
            }
            reponse = llmClient.chat(question);
            roleSystemeChangeable = false;
            conversation.append("== User:\n").append(question)
                    .append("\n== Gemini:\n").append(reponse).append("\n");
        } catch (RuntimeException e) {
            // Les exceptions du fournisseur peuvent contenir des informations sensibles.
            afficherErreur("Gemini n'a pas pu répondre. Vérifiez GEMINI_KEY et l'accès au service, puis réessayez.");
        }
        return null;
    }

    public String nouveauChat() {
        // Efface aussi la mémoire métier ; le redirect crée ensuite une nouvelle vue CDI.
        llmClient.setSystemRole(roleSysteme);
        return "/index.xhtml?faces-redirect=true";
    }

    private void afficherErreur(String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Envoi impossible", detail));
    }

    public List<SelectItem> getRolesSysteme() {
        if (listeRolesSysteme == null) {
            listeRolesSysteme = new ArrayList<>();
            listeRolesSysteme.add(new SelectItem("""
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """, "Assistant"));
            listeRolesSysteme.add(new SelectItem("""
                    You are an interpreter. You translate from English to French and from French to English.
                    If the user type a French text, you translate it into English.
                    If the user type an English text, you translate it into French.
                    If the text contains only one to three words, give some examples of usage of these words in English.
                    """, "Traducteur Anglais-Français"));
            listeRolesSysteme.add(new SelectItem("""
                    You are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit in the country or the town
                    and you tell them the average price of a meal.
                    """, "Guide touristique"));
        }
        return listeRolesSysteme;
    }

    public String getRoleSysteme() { return roleSysteme; }
    public void setRoleSysteme(String roleSysteme) { this.roleSysteme = roleSysteme; }
    public boolean isRoleSystemeChangeable() { return roleSystemeChangeable; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }
    public String getConversation() { return conversation.toString(); }
}
