# Android: instalacja i wgrywanie aplikacji ESP Cam Drive (Windows, Linux i macOS)

Nov 14, 2026

## Zanim zaczniesz

Ta instrukcja pokazuje, jak zbudować aplikację ESP Cam Drive na Androida i wgrać ją na telefon. Są trzy wersje: Windows, Linux i macOS. Każda ma dwie części: konfigurację od zera i ponowne wgranie aplikacji. Na Androidzie jest dużo prościej niż na iPhonie: nie potrzebujesz Apple ID, Xcode ani żadnych obejść, a aplikacja nie wygasa po 7 dniach.

**Czego potrzebujesz**

- Telefon z Androidem 8.0 lub nowszym i kabel USB, który przesyła dane (nie tylko ładuje).
- Ok. 8 GB wolnego miejsca (Android Studio, SDK i biblioteki).
- Kod aplikacji: folder `android/ESPCamDrive` z repozytorium (sklonowanego albo pobranego z gita). W instrukcji jego ścieżkę zapisano jako SCIEZKA\_DO\_REPO.
- Client ID z Google Cloud, jeśli chcesz wysyłać pliki na Dysk Google (patrz krok 4).

**Dwie drogi instalacji**

- **Droga A (USB):** telefon podłączony kablem, komenda `installDebug` wgrywa aplikację sama. Zalecana.
- **Droga B (plik APK):** budujesz plik `app-debug.apk`, wysyłasz go na telefon (kabel, e-mail, chmura) i instalujesz ręcznie. Przydaje się, gdy nie chcesz włączać debugowania USB.

**Gdzie wpisywać komendy**

| Etykieta | Co to jest | Jak otworzyć |
| --- | --- | --- |
| \[PowerShell\] | Okno poleceń Windowsa | Start, wpisz PowerShell |
| \[Terminal\] | Okno poleceń Linuxa lub macOS | Linux: Ctrl+Alt+T. macOS: Cmd+Spacja, wpisz Terminal |
| \[Przeglądarka\] | Zwykła przeglądarka | |
| \[Telefon\] | Ekran telefonu z Androidem | |

- Komendy kopiuj i wklejaj, nie przepisuj.
- Słowa WIELKIMI LITERAMI w komendach (np. TWOJLOGIN) to miejsca do podmiany na własne dane.
- Komendy wpisuj w folderze projektu (tam, gdzie leży plik `gradlew`). W krokach poniżej jest to `ESPCamDrive`.

**Jaka wersja Javy.** Do budowania potrzebna jest Java (JDK) w wersji 17 lub 21. Nowsze wersje (np. 24 albo 25) nie działają z tym projektem i kończą się błędem. Android Studio ma własną Javę i z nią wszystko działa, dlatego w krokach poniżej korzystamy z niej.

## Windows, część 1: konfiguracja od zera

### Krok 1. Zainstaluj Android Studio

**\[Przeglądarka\]** Wejdź na `https://developer.android.com/studio`, pobierz Android Studio i zainstaluj je z ustawieniami domyślnymi. Przy pierwszym uruchomieniu kreator pobierze Android SDK. Zgódź się na wszystko, co proponuje, i poczekaj do końca (kilkanaście minut). Potem możesz zamknąć Android Studio.

Android SDK musi mieć platformę **Android 15 (API 35)**. Jeśli jej nie ma, Gradle dopobierze ją sam przy pierwszym budowaniu, o ile licencje zostały zaakceptowane w kreatorze.

### Krok 2. Skopiuj projekt

**\[PowerShell\]** Skopiuj folder projektu do folderu domowego (podmień SCIEZKA\_DO\_REPO na ścieżkę do repozytorium):

```
cd $HOME
Copy-Item -Recurse -Force SCIEZKA_DO_REPO\android\ESPCamDrive $HOME
cd $HOME\ESPCamDrive
dir
```

W wyniku mają być m.in. `gradlew.bat`, `build.gradle.kts` i folder `app`. Jeśli ich nie widzisz, a jest tylko drugi folder `ESPCamDrive`, wejdź do niego: `cd ESPCamDrive`.

### Krok 3. Wskaż Javę z Android Studio

**\[PowerShell\]** Za każdym razem w nowym oknie PowerShell, w którym budujesz aplikację:

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path += ";$env:ANDROID_HOME\platform-tools"
java -version
```

`java -version` ma wypisać wersję 17 lub 21. Jeśli Android Studio zainstalowano w innym miejscu, popraw ścieżkę do folderu `jbr`.

### Krok 4. Ustaw logowanie do Google (Client ID)

Bez tego kroku aplikacja zrobi zdjęcia, ale nie wyśle ich na Dysk. Wykonaj podpunkty po kolei, w tej kolejności. SHA-1 musisz mieć przed założeniem klienta w Google Cloud.

**4a. Pobierz odcisk SHA-1 (\[PowerShell\])**

```
cd $HOME\ESPCamDrive
.\gradlew.bat signingReport
```

Pierwsze uruchomienie trwa kilka minut, bo Gradle pobiera siebie i biblioteki. Znajdź sekcję `Variant: debug` i skopiuj linijkę `SHA1:` (ciąg par znaków oddzielonych dwukropkami). Jeśli nie ma w niej `SHA1:` albo komenda pisze, że nie znaleziono keystore, utwórz klucz przez zbudowanie aplikacji i powtórz:

```
.\gradlew.bat assembleDebug
.\gradlew.bat signingReport
```

Inny sposób, bez Gradle (klucz musi już istnieć):

```
& "$env:JAVA_HOME\bin\keytool" -list -v -keystore $HOME\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Odcisk zależy od klucza debug na tym komputerze (plik `.android\debug.keystore` w folderze domowym). Aplikację instaluj z tego samego komputera, z którego pochodzi odcisk.

**4b. Załóż klienta w Google Cloud (\[Przeglądarka\])**

Wejdź na `https://console.cloud.google.com` i wybierz swój projekt (lub utwórz nowy).

1. Interfejsy API i usługi, Biblioteka: znajdź Google Drive API i kliknij Włącz.
2. Ekran zgody OAuth (Google Auth Platform): skonfiguruj aplikację, jeśli jeszcze nie jest. W odbiorcach dodaj swoje konto Google jako użytkownika testowego. W dostępie do danych dodaj zakres `.../auth/drive.file`.
3. Dane logowania, Utwórz dane logowania, Identyfikator klienta OAuth.
4. Typ aplikacji: **Android**.
5. Nazwa: dowolna, np. `ESP Cam Drive Android`.
6. Nazwa pakietu: `com.ola.espcamdrive`
7. Odcisk cyfrowy certyfikatu SHA-1: wklej wartość z 4a.
8. Kliknij Utwórz i skopiuj Client ID (kończy się na `.apps.googleusercontent.com`).

**4c. Wpisz Client ID do aplikacji (\[PowerShell\])**

```
notepad $HOME\ESPCamDrive\app\src\main\java\com\ola\espcamdrive\Config.kt
```

Zamień `WKLEJ_TUTAJ_CLIENT_ID.apps.googleusercontent.com` na swój Client ID (zostaw cudzysłowy) i zapisz plik. Schemat przekierowania w `AndroidManifest.xml` uzupełnia się sam na podstawie tego pliku, nic więcej nie zmieniasz. Sprawdź:

```
Select-String googleClientID $HOME\ESPCamDrive\app\src\main\java\com\ola\espcamdrive\Config.kt
```

Ma pokazać Twój Client ID, a nie napis WKLEJ\_TUTAJ. Jeśli aplikacja była już zbudowana w 4a, w kroku 6 zostanie zbudowana ponownie z nowym Client ID.

**Gdy Google odrzuca logowanie.** Po kliknięciu "Zaloguj się do Google" błąd o nieprawidłowym przekierowaniu (redirect\_uri) albo o niezgodnej aplikacji oznacza zwykle złą nazwę pakietu, zły SHA-1 albo Client ID wpisany do `Config.kt` z innego klienta. Poprawiasz dane w Google Cloud albo w `Config.kt` i budujesz aplikację ponownie. Client ID typu iOS (ten z aplikacji na iPhone'a) może zadziałać zamiast klienta Android, ale tego nie sprawdzaliśmy na żywo. Zalecany jest klient typu Android z 4b.

### Krok 5. Włącz debugowanie USB na telefonie (tylko droga A)

**\[Telefon\]**

1. Ustawienia, Informacje o telefonie, siedem razy kliknij Numer kompilacji. Telefon napisze, że jesteś programistą.
2. Ustawienia, System, Opcje programisty (nazwy różnią się między producentami), włącz Debugowanie USB.
3. Podłącz telefon kablem. Na ekranie pojawi się pytanie "Zezwolić na debugowanie USB?". Zaznacz "Zawsze zezwalaj" i kliknij Zezwól.

**\[PowerShell\]** Sprawdź, czy komputer widzi telefon:

```
adb devices
```

Telefon ma być na liście ze słowem `device` (nie `unauthorized`). Jeśli jest `unauthorized`, odblokuj telefon i kliknij Zezwól. Jeśli lista jest pusta, sprawdź kabel (musi przesyłać dane) i punkty 1 do 3.

### Krok 6. Zbuduj i zainstaluj aplikację

**\[PowerShell\]**

```
cd $HOME\ESPCamDrive
.\gradlew.bat installDebug
```

Na końcu zobaczysz `BUILD SUCCESSFUL`, a aplikacja ESP Cam Drive pojawi się na telefonie (lub zaktualizuje się, jeśli była już zbudowana w kroku 4).

**Droga B (sam plik APK).** Zamiast `installDebug` wpisz:

```
.\gradlew.bat assembleDebug
```

Gotowy plik leży w `app\build\outputs\apk\debug\app-debug.apk`. Wyślij go na telefon i otwórz. Telefon zapyta o zgodę na instalację z nieznanych źródeł dla aplikacji, z której otwierasz plik (np. Pliki, Chrome). Zezwól i zainstaluj.

### Krok 7. Uruchom aplikację

**\[Telefon\]** Otwórz ESP Cam Drive. Ustawienia otworzą się same. Wpisz adres IP kamery (telefon musi być w tej samej sieci WiFi), kliknij "Zaloguj się do Google" i zalogowanie dokończ w przeglądarce. Przy pierwszym starcie trybu cyklicznego zezwól na powiadomienia: to właśnie powiadomienie utrzymuje aplikację przy życiu po zgaszeniu ekranu.

Jeśli telefon ma włączone oszczędzanie baterii, wyłącz je dla tej aplikacji (Ustawienia, Aplikacje, ESP Cam Drive, Bateria, Bez ograniczeń), inaczej niektóre telefony (Xiaomi, Huawei, Samsung) mogą zatrzymać cykl po dłuższym czasie.

## Windows, część 2: ponowne wgranie aplikacji

Nie powtarzasz instalacji Android Studio. Aplikacja nie wygasa po 7 dniach, więc wgrywasz ją ponownie tylko wtedy, gdy masz nową wersję kodu.

| Sytuacja | Co zrobić |
| --- | --- |
| Masz nową wersję kodu (nowy kod z repozytorium) | Bloki A, B, C |
| Zmiana samego Client ID | Blok B (tylko edycja pliku), potem C |

### A. Przygotowanie

**\[Telefon\]** Podłącz telefon kablem, odblokuj go i w razie pytania zezwól na debugowanie. **\[PowerShell\]** Wpisz blok z kroku 3 części 1 (JAVA\_HOME, ANDROID\_HOME), a potem `adb devices`.

### B. Nowa wersja kodu

Zapisz swój `Config.kt` na bok, skopiuj nowy kod i przywróć plik:

```
cd $HOME
Copy-Item ESPCamDrive\app\src\main\java\com\ola\espcamdrive\Config.kt $HOME\Config.kt.bak
Copy-Item -Recurse -Force SCIEZKA_DO_REPO\android\ESPCamDrive $HOME
Copy-Item $HOME\Config.kt.bak ESPCamDrive\app\src\main\java\com\ola\espcamdrive\Config.kt -Force
Select-String googleClientID ESPCamDrive\app\src\main\java\com\ola\espcamdrive\Config.kt
```

Ostatnia komenda ma pokazać Twój Client ID. Jeśli nowa wersja usuwa jakieś pliki źródłowe, `Copy-Item` ich nie skasuje. Wtedy usuń najpierw stary folder (`Remove-Item -Recurse -Force $HOME\ESPCamDrive`), skopiuj projekt ponownie i wpisz Client ID od nowa.

### C. Instalacja

```
cd $HOME\ESPCamDrive
.\gradlew.bat installDebug
```

Nowa wersja nadpisuje starą, a zalogowanie do Google i ustawienia zostają. Nie zmieniaj `applicationId` w `app\build.gradle.kts` (domyślnie `com.ola.espcamdrive`), bo wtedy powstałaby druga aplikacja, a Google Cloud przestałby się zgadzać.

### Gdy coś nie działa

| Objaw | Przyczyna | Co zrobić |
| --- | --- | --- |
| `adb devices` pokazuje pustą listę | Kabel tylko do ładowania, brak debugowania USB albo brak sterownika | Zmień kabel, sprawdź krok 5. U niektórych producentów trzeba doinstalować sterownik USB (np. Samsung, Xiaomi) |
| `adb devices` pokazuje `unauthorized` | Telefon nie zaufał komputerowi | Odblokuj telefon i kliknij Zezwól |
| `Unsupported class file major version` albo `Could not determine java version` | Za nowa Java (24, 25) | Ustaw JAVA\_HOME na `jbr` z Android Studio (krok 3) |
| `SDK location not found` | Gradle nie wie, gdzie jest SDK | Ustaw ANDROID\_HOME (krok 3) albo otwórz projekt raz w Android Studio |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Na telefonie jest wersja podpisana innym kluczem (np. zbudowana na innym komputerze) | Odinstaluj aplikację z telefonu i zainstaluj ponownie (zalogujesz się do Google od nowa) |
| `Failed to find Build Tools` albo `licences not accepted` | SDK bez licencji | Otwórz Android Studio, SDK Manager, zaakceptuj licencje, albo `sdkmanager --licenses` |
| Gradle wisi na `Downloading ... gradle-8.14.3-bin.zip` | Wolne albo zablokowane łącze | Poczekaj, sprawdź połączenie i proxy firmowe |
| Logowanie do Google kończy się błędem o przekierowaniu | Client ID nie pasuje do aplikacji | Krok 4: klient typu Android z nazwą pakietu i SHA-1 |
| Logowanie wraca do przeglądarki i nic się nie dzieje | Schemat przekierowania nie zgadza się z Client ID | Sprawdź `Config.kt` i zbuduj aplikację ponownie |
| Aplikacja nie łączy się z kamerą | Telefon w innej sieci WiFi albo zły adres IP | Ta sama sieć WiFi, sprawdź adres w przeglądarce telefonu: `http://ADRES/capture` powinien pokazać zdjęcie |
| Cykl zatrzymuje się po zgaszeniu ekranu | Oszczędzanie baterii zabija aplikację | Krok 7: bateria bez ograniczeń, zezwól na powiadomienia |

## Linux, część 1: konfiguracja od zera

Komendy są dla Ubuntu i Debiana. Na innych dystrybucjach nazwy pakietów mogą się różnić.

### Krok 1. Zainstaluj potrzebne pakiety

**\[Terminal\]**

```
sudo apt update
sudo apt install -y openjdk-21-jdk unzip curl adb
```

Jeśli `apt` nie zna `openjdk-21-jdk`, użyj `openjdk-17-jdk`. Sprawdź: `java -version` (ma pokazać 17 lub 21). Jeśli masz kilka wersji Javy, wskaż właściwą: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.

### Krok 2. Zainstaluj Android SDK

Masz dwie możliwości. Prostsza: zainstaluj Android Studio z `https://developer.android.com/studio`, uruchom je raz i przejdź przez kreator (SDK ląduje w `$HOME/Android/Sdk`). Bez Android Studio, same narzędzia:

**\[Terminal\]**

```
mkdir -p $HOME/Android/Sdk/cmdline-tools
cd $HOME/Android/Sdk/cmdline-tools
curl -fLO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest
export ANDROID_HOME=$HOME/Android/Sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Numer w nazwie pliku `commandlinetools-linux-...` to wersja z dnia pisania. Aktualny link znajdziesz na stronie pobierania Android Studio w sekcji "Command line tools only".

### Krok 3. Skopiuj projekt i ustaw Client ID

**\[Terminal\]**

```
cd $HOME
cp -R SCIEZKA_DO_REPO/android/ESPCamDrive $HOME/
cd ESPCamDrive
ls
nano app/src/main/java/com/ola/espcamdrive/Config.kt
```

W wyniku `ls` mają być m.in. `gradlew`, `build.gradle.kts` i folder `app`. W edytorze zamień `WKLEJ_TUTAJ_CLIENT_ID.apps.googleusercontent.com` na swój Client ID (zostaw cudzysłowy). Zapisz klawiszami Ctrl+O i Enter, wyjdź przez Ctrl+X. Sprawdź:

```
grep googleClientID app/src/main/java/com/ola/espcamdrive/Config.kt
```

Jak założyć klienta w Google Cloud (klient typu Android, nazwa pakietu `com.ola.espcamdrive`, odcisk SHA-1), opisuje krok 4 części Windows. SHA-1 pobierzesz dopiero po pierwszym zbudowaniu (krok 5 poniżej), więc kolejność jest taka: zbuduj raz z placeholderem, pobierz SHA-1, załóż klienta, wpisz Client ID do `Config.kt` i zbuduj ponownie.

### Krok 4. Włącz debugowanie USB na telefonie

Tak samo jak w kroku 5 części Windows. Potem **\[Terminal\]**:

```
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
adb devices
```

Telefon ma być na liście ze słowem `device`. Jeśli lista jest pusta, a kabel jest dobry, może brakować reguł udev: `sudo apt install -y android-sdk-platform-tools-common`, potem odepnij i podepnij kabel.

### Krok 5. Zbuduj i zainstaluj aplikację

**\[Terminal\]**

```
cd $HOME/ESPCamDrive
chmod +x gradlew
./gradlew installDebug
```

Pierwsze budowanie trwa kilka minut. Na końcu `BUILD SUCCESSFUL`, a aplikacja jest na telefonie. Odcisk SHA-1 do Google Cloud pobierzesz komendą `./gradlew signingReport` (sekcja `Variant: debug`, linijka `SHA1:`). Sam plik APK (droga B): `./gradlew assembleDebug`, plik leży w `app/build/outputs/apk/debug/app-debug.apk`.

### Krok 6. Uruchom aplikację

Tak samo jak krok 7 części Windows: adres IP kamery, logowanie do Google, zgoda na powiadomienia, bateria bez ograniczeń.

## Linux, część 2: ponowne wgranie aplikacji

**\[Terminal\]** Podłącz telefon, odblokuj go i sprawdź `adb devices` (z `ANDROID_HOME` i `PATH` jak w kroku 4). Przy nowej wersji kodu zachowaj swój Client ID:

```
cd $HOME
cp ESPCamDrive/app/src/main/java/com/ola/espcamdrive/Config.kt $HOME/Config.kt.bak
cp -R SCIEZKA_DO_REPO/android/ESPCamDrive $HOME/
cp $HOME/Config.kt.bak ESPCamDrive/app/src/main/java/com/ola/espcamdrive/Config.kt
grep googleClientID ESPCamDrive/app/src/main/java/com/ola/espcamdrive/Config.kt
cd ESPCamDrive
chmod +x gradlew
./gradlew installDebug
```

`grep` ma pokazać Twój Client ID. Jeśli nowa wersja usuwa pliki źródłowe, `cp -R` ich nie skasuje: usuń najpierw stary folder (`rm -r $HOME/ESPCamDrive`), skopiuj projekt ponownie i wpisz Client ID od nowa. Problemy: tabela "Gdy coś nie działa" w części Windows (komendy zamień na odpowiedniki z `./gradlew`).

## macOS, część 1: konfiguracja od zera

### Krok 1. Zainstaluj Javę i Android Studio

**\[Terminal\]** Z Homebrew:

```
brew install openjdk@21
brew install --cask android-studio
```

Albo pobierz Android Studio z `https://developer.android.com/studio`. Uruchom je raz i przejdź przez kreator (pobierze SDK do `$HOME/Library/Android/sdk`). Potem zamknij.

### Krok 2. Skopiuj projekt i ustaw Client ID

**\[Terminal\]**

```
cd $HOME
cp -R SCIEZKA_DO_REPO/android/ESPCamDrive $HOME/
cd ESPCamDrive
ls
open -e app/src/main/java/com/ola/espcamdrive/Config.kt
```

Zamień `WKLEJ_TUTAJ_CLIENT_ID.apps.googleusercontent.com` na swój Client ID (zostaw cudzysłowy), zapisz i sprawdź:

```
grep googleClientID app/src/main/java/com/ola/espcamdrive/Config.kt
```

Jak założyć klienta w Google Cloud, opisuje krok 4 części Windows. SHA-1 pobierzesz dopiero po pierwszym zbudowaniu (krok 4 poniżej), więc kolejność jest taka: zbuduj raz z placeholderem, pobierz SHA-1 (`./gradlew signingReport`), załóż klienta, wpisz Client ID do `Config.kt` i zbuduj ponownie.

### Krok 3. Włącz debugowanie USB na telefonie i sprawdź połączenie

Tak jak w kroku 5 części Windows. Potem **\[Terminal\]**:

```
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
adb devices
```

Jeśli `java_home -v 21` zgłasza brak takiej wersji, użyj Javy z Android Studio: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Zmienne trzeba ustawić w każdym nowym oknie Terminala.

### Krok 4. Zbuduj i zainstaluj aplikację

**\[Terminal\]**

```
cd $HOME/ESPCamDrive
chmod +x gradlew
./gradlew installDebug
```

Pierwsze budowanie trwa kilka minut. Odcisk SHA-1: `./gradlew signingReport`. Sam plik APK: `./gradlew assembleDebug`, plik w `app/build/outputs/apk/debug/app-debug.apk`.

### Krok 5. Uruchom aplikację

Tak samo jak krok 7 części Windows.

## macOS, część 2: ponowne wgranie aplikacji

Tak samo jak w części 2 dla Linuxa: ustaw zmienne z kroku 3, sprawdź `adb devices`, zachowaj `Config.kt`, skopiuj nowy kod, przywróć plik i wpisz `./gradlew installDebug`.

## Najczęstsze pytania

**Czy aplikacja wygasa po tygodniu jak na iPhonie?** Nie. Aplikacja z `installDebug` nie ma limitu czasu. Nie potrzebujesz też żadnego konta dewelopera.

**Czy mogę obejść się bez kabla?** Tak. Droga B (plik APK) nie wymaga ani kabla, ani debugowania USB. Alternatywa: Debugowanie bezprzewodowe (Opcje programisty), a w komputerze `adb pair ADRES:PORT` i `adb connect ADRES:PORT`.

**Gdzie są zdjęcia na telefonie?** W folderze aplikacji: `Android/data/com.ola.espcamdrive/files/ESPCam`. Przy błędzie wysyłki plik tam zostaje. W trybie cyklicznym z włączonym "Usuń z telefonu po wysłaniu" udane wysyłki są kasowane.

**Dlaczego aplikacja pokazuje powiadomienie?** To powiadomienie usługi pierwszoplanowej. Dzięki niemu Android nie zawiesza aplikacji po zgaszeniu ekranu i cykl zdjęć działa dalej. Znika po kliknięciu "Zatrzymaj". Na iPhonie tę rolę pełni lokalizacja w tle.

**Czy mogę zmienić nazwę pakietu?** Możesz, ale musisz wtedy zmienić `applicationId` i `namespace` w `app/build.gradle.kts`, a w Google Cloud utworzyć klienta z nowym pakietem. Najprościej nie zmieniać.

**Czy wysyłka na Dysk działa, gdy aplikacja jest w tle?** Tak, dopóki trwa tryb cykliczny (usługa utrzymuje aplikację przy życiu). Na iPhonie wysyłkę dokańcza system, na Androidzie robi to sama aplikacja, więc po zabiciu jej przez system w trakcie wysyłki ta jedna wysyłka zostanie przerwana, a plik zostanie na telefonie.

### Źródła

- [Android Studio](https://developer.android.com/studio): pobieranie, instalacja i narzędzia wiersza poleceń.
- [Opcje programisty i debugowanie USB](https://developer.android.com/studio/debug/dev-options): włączanie debugowania na telefonie.
- [Android Debug Bridge (adb)](https://developer.android.com/tools/adb): polecenia adb, debugowanie bezprzewodowe.
- [OAuth 2.0 dla aplikacji mobilnych (Google)](https://developers.google.com/identity/protocols/oauth2/native-app): logowanie z PKCE i przekierowaniem przez własny schemat.
