package utils.voice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class TombariVoiceAssistant {

    private static final String SCRIPT = """
            Add-Type -AssemblyName System.Speech
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            $OutputEncoding = [System.Text.Encoding]::UTF8
            $recognizer = New-Object System.Speech.Recognition.SpeechRecognitionEngine
            $choices = New-Object System.Speech.Recognition.Choices
            $choices.Add('hello tombari')
            $choices.Add('merci')
            $choices.Add('je veux entrer dans l application')
            $choices.Add('je veux naviguer dans l application')
            $choices.Add('veuillez allez pour faire le sign in')
            $choices.Add('je veux me connecter')
            $choices.Add('ouvre login')
            $choices.Add('ouvrir sign in')
            $choices.Add('aller vers login')
            $choices.Add('allez vers le login')
            $choices.Add('veuillez aller vers login')
            $choices.Add('j ai deja un compte')
            $choices.Add('retour au login')
            $choices.Add('je veux creer un compte')
            $choices.Add('ouvrir create account')
            $choices.Add('allez vers signup')
            $choices.Add('allez au signup')
            $grammarBuilder = New-Object System.Speech.Recognition.GrammarBuilder($choices)
            $grammar = New-Object System.Speech.Recognition.Grammar($grammarBuilder)
            $recognizer.LoadGrammar($grammar)
            $recognizer.SetInputToDefaultAudioDevice()

            $speaker = New-Object System.Speech.Synthesis.SpeechSynthesizer
            $speaker.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Male)

            while ($true) {
                $result = $recognizer.Recognize()
                if ($null -eq $result) {
                    continue
                }

                $text = $result.Text
                if ($text -eq 'hello tombari') {
                    $speaker.Speak('Bonjour, comment puis-je vous aider aujourd''hui ?')
                } elseif ($text -eq 'merci') {
                    $speaker.Speak('Pas de souci, je suis toujours a votre disposition.')
                } elseif ($text -eq 'je veux entrer dans l application' -or $text -eq 'je veux naviguer dans l application') {
                    $speaker.Speak('Il faut faire le login ou creer un compte en cliquant sur Create account.')
                } elseif ($text -eq 'veuillez allez pour faire le sign in' -or $text -eq 'je veux me connecter' -or $text -eq 'ouvre login' -or $text -eq 'ouvrir sign in' -or $text -eq 'aller vers login' -or $text -eq 'allez vers le login' -or $text -eq 'veuillez aller vers login' -or $text -eq 'j ai deja un compte' -or $text -eq 'retour au login') {
                    $speaker.Speak('D''accord, je vous redirige vers la page Sign in.')
                    Write-Output 'NAVIGATE_SIGNIN'
                } elseif ($text -eq 'je veux creer un compte' -or $text -eq 'ouvrir create account' -or $text -eq 'allez vers signup' -or $text -eq 'allez au signup') {
                    $speaker.Speak('D''accord, je vous redirige vers la page Create account.')
                    Write-Output 'NAVIGATE_SIGNUP'
                }
            }
            """;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService outputReaderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tombari-voice-output-reader");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Process process;
    private volatile Consumer<String> commandListener;

    public void setCommandListener(Consumer<String> commandListener) {
        this.commandListener = commandListener;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            String encodedScript = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", encodedScript
            ).start();

            outputReaderExecutor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running.get() && (line = reader.readLine()) != null) {
                        String command = line
                                .replace("\u0000", "")
                                .replace("\uFEFF", "")
                                .trim();
                        if (command.isEmpty()) {
                            continue;
                        }
                        Consumer<String> listener = commandListener;
                        if (listener != null) {
                            listener.accept(command);
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            running.set(false);
            System.err.println("[TombariVoiceAssistant] Impossible de démarrer l'assistant vocal: " + e.getMessage());
        }
    }

    public void stop() {
        running.set(false);
        Process current = process;
        process = null;
        if (current != null && current.isAlive()) {
            current.destroyForcibly();
        }
    }
}
