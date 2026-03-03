package utils.voice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class FarahVoiceAssistant {

    private static final String SCRIPT = """
            Add-Type -AssemblyName System.Speech
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            $OutputEncoding = [System.Text.Encoding]::UTF8

            $recognizer = $null
            try {
                $installed = [System.Speech.Recognition.SpeechRecognitionEngine]::InstalledRecognizers()
                $ar = $installed | Where-Object { $_.Culture.Name -like 'ar*' } | Select-Object -First 1
                if ($null -ne $ar) {
                    $recognizer = New-Object System.Speech.Recognition.SpeechRecognitionEngine($ar)
                } elseif ($installed.Count -gt 0) {
                    $recognizer = New-Object System.Speech.Recognition.SpeechRecognitionEngine($installed[0])
                } else {
                    $recognizer = New-Object System.Speech.Recognition.SpeechRecognitionEngine
                }
            } catch {
                $recognizer = New-Object System.Speech.Recognition.SpeechRecognitionEngine
            }

            $choices = New-Object System.Speech.Recognition.Choices
            $choices.Add('اهلا فرح')
            $choices.Add('مرحبا فرح')
            $choices.Add('يا فرح')
            $choices.Add('farah')
            $choices.Add('ahla farah')
            $choices.Add('marhba farah')
            $choices.Add('ya farah')
            $choices.Add('salut farah')
            $choices.Add('bonjour farah')
            $choices.Add('شكرا')
            $choices.Add('نحب ندخل للاپليكاسيون')
            $choices.Add('نحب نعمل تسجيل دخول')
            $choices.Add('افتح تسجيل الدخول')
            $choices.Add('امشي لتسجيل الدخول')
            $choices.Add('عندي حساب')
            $choices.Add('ارجع لتسجيل الدخول')
            $choices.Add('نحب نعمل حساب')
            $choices.Add('افتح انشاء حساب')
            $choices.Add('امشي لانشاء حساب')

            $grammarBuilder = New-Object System.Speech.Recognition.GrammarBuilder($choices)
            $grammar = New-Object System.Speech.Recognition.Grammar($grammarBuilder)
            $recognizer.LoadGrammar($grammar)
            $recognizer.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar))
            $recognizer.SetInputToDefaultAudioDevice()

            $speakerFemale = New-Object System.Speech.Synthesis.SpeechSynthesizer
            $isArabicVoice = $false

            $arabicFemaleVoiceCandidates = @(
                'Microsoft Hoda Desktop',
                'Microsoft Naayf Desktop',
                'Microsoft Salma Desktop',
                'Hoda',
                'Naayf',
                'Salma'
            )

            $voiceSelected = $false
            foreach ($voiceName in $arabicFemaleVoiceCandidates) {
                try {
                    $speakerFemale.SelectVoice($voiceName)
                    $voiceSelected = $true
                    break
                } catch {
                }
            }

            try {
                if (-not $voiceSelected) {
                    $speakerFemale.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Female, [System.Speech.Synthesis.VoiceAge]::Adult, 0, (New-Object System.Globalization.CultureInfo('ar-SA')))
                    $voiceSelected = $true
                }
            } catch {
            }

            if (-not $voiceSelected) {
                try {
                    $speakerFemale.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Female)
                } catch {
                }
            }

            try {
                $currentVoice = $speakerFemale.Voice
                if ($null -ne $currentVoice -and $null -ne $currentVoice.Culture -and $currentVoice.Culture.Name -like 'ar*') {
                    $isArabicVoice = $true
                }
                if ($null -ne $currentVoice -and $null -ne $currentVoice.Culture) {
                    Write-Output ("FARAH_VOICE: " + $currentVoice.Name + " | " + $currentVoice.Culture.Name)
                }
            } catch {
            }

            if (-not $isArabicVoice) {
                Write-Output 'FARAH_ARABIC_VOICE_MISSING'
            }

            function Say-TN {
                param(
                    [string]$arabicText,
                    [string]$latinFallback
                )
                if ($isArabicVoice) {
                    $speakerFemale.Speak($arabicText)
                } else {
                    $speakerFemale.Speak($latinFallback)
                }
            }

            Write-Output 'FARAH_READY'

            while ($true) {
                $result = $recognizer.Recognize()
                if ($null -eq $result) {
                    continue
                }

                $text = $result.Text
                $normalized = $text.ToLowerInvariant().Replace("'", "").Replace("’", "").Trim()
                Write-Output ("FARAH_HEARD: " + $text)

                if ($normalized -match '(فرح|farah)') {
                    Say-TN 'يعايشك، أنا فرح. آش نجم نعاونك؟' 'Yaaychek, ena Farah. Ach nejjem n3awnek?'
                } elseif ($normalized -match '(شكرا|chokran|merci)') {
                    Say-TN 'لا شكر على واجب، ديما في الخدمة.' 'La chokr ala wejeb, dima fil khidma.'
                } elseif (($normalized -like '*ندخل*' -and ($normalized -like '*اپليكاسيون*' -or $normalized -like '*ابليكاسيون*')) -or $normalized -like '*nheb nodkhol*' -or $normalized -like '*application*') {
                    Say-TN 'يلزمك يا تعمل تسجيل دخول، يا تعمل حساب جديد.' 'Yelzmek ya taamel sign in, ya taamel compte jdid.'
                } elseif ($normalized -like '*تسجيل دخول*' -or $normalized -like '*افتح تسجيل الدخول*' -or $normalized -like '*امشي لتسجيل الدخول*' -or $normalized -like '*عندي حساب*' -or $normalized -like '*ارجع لتسجيل الدخول*' -or $normalized -like '*signin*' -or $normalized -like '*sign in*' -or $normalized -like '*login*') {
                    Say-TN 'حاضر، تو نحلّلك صفحة تسجيل الدخول.' 'Hather, taw nhellek safhet sign in.'
                    Write-Output 'NAVIGATE_SIGNIN'
                } elseif ($normalized -like '*نحب نعمل حساب*' -or $normalized -like '*افتح انشاء حساب*' -or $normalized -like '*امشي لانشاء حساب*' -or $normalized -like '*signup*' -or $normalized -like '*sign up*' -or $normalized -like '*create account*') {
                    Say-TN 'حاضر، تو نحلّلك صفحة إنشاء حساب.' 'Hather, taw nhellek safhet create account.'
                    Write-Output 'NAVIGATE_SIGNUP'
                }
            }
            """;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService outputReaderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "farah-voice-output-reader");
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
                ).redirectErrorStream(true).start();

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
            System.err.println("[FarahVoiceAssistant] Impossible de démarrer l'assistante vocale: " + e.getMessage());
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
