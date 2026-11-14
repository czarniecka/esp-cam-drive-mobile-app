# xtool: instalacja i wgrywanie aplikacji na iPhone'a (Windows i Linux)

Nov 14, 2026

## Zanim zaczniesz

Ta instrukcja pokazuje, jak zbudować aplikację ESP Cam Drive bez Maca i wgrać ją na iPhone'a narzędziem xtool. Są dwie wersje: Windows (przez WSL, czyli Ubuntu w Windowsie) i Linux. Każda ma dwie części: konfigurację od zera i ponowne wgranie aplikacji.

**Czego potrzebujesz**

- iPhone i kabel USB, który przesyła dane (nie tylko ładuje).
- Apple ID. To jest adres e-mail konta. Wystarczy darmowe konto, ale lepiej założyć osobne, zapasowe, bo xtool używa nieoficjalnych interfejsów Apple.
- Ok. 60 GB wolnego miejsca. Sam plik Xcode.xip ma kilkanaście GB, a po nim powstaje jeszcze SDK.
- Paczkę ESPCamDrive.zip z kodem aplikacji.
- Client ID z Google Cloud (typ klienta: iOS), jeśli chcesz wysyłać pliki na Dysk Google.

**Gdzie wpisywać komendy**

Najwięcej pomyłek bierze się stąd, że nie wiadomo, w którym oknie coś wpisać. Dlatego przy każdym kroku stoi etykieta:

| Etykieta | Co to jest | Jak otworzyć |
| --- | --- | --- |
| \[PowerShell, administrator\] | Okno poleceń Windowsa z uprawnieniami administratora | Start, wpisz PowerShell, prawy przycisk myszy, Uruchom jako administrator |
| \[Ubuntu\] | Czarne okno z tekstem, w którym widać coś w rodzaju `twojlogin@komputer:~$` | Start, wpisz Ubuntu |
| \[Przeglądarka\] | Zwykła przeglądarka w Windowsie |  |
| \[Eksplorator\] | Okno z plikami w Windowsie | Wpisz w Ubuntu `explorer.exe .` (z kropką), otworzy się folder domowy Ubuntu |
| \[Telefon\] | Ekran iPhone'a |  |

**Podstawy obsługi okna Ubuntu**

- Ubuntu w WSL to tylko tekst, bez pulpitu i okienek. To jest normalne i niczego więcej nie potrzebujesz.
- Wklejanie: prawy przycisk myszy w oknie albo Ctrl+Shift+V. Po wklejeniu naciśnij Enter i poczekaj, aż znów pojawi się znak `$`.
- Kopiowanie z okna: zaznacz tekst myszką i naciśnij Ctrl+Shift+C. Samo Ctrl+C, gdy nic nie jest zaznaczone, przerywa działający program.
- Hasła przy `sudo` nie widać podczas pisania. To też normalne, wpisz je i naciśnij Enter.
- Komendy kopiuj i wklejaj, nie przepisuj. Łatwo pomylić dużą literę O z zerem, pominąć kropkę albo spację, a tyldę (`~`) na polskiej klawiaturze trudno wpisać.
- Słowa WIELKIMI LITERAMI w komendach (np. TWOJANAZWA) to miejsca do podmiany na własne dane. Reszty nie zmieniaj.
- Hasło do Apple ID wpisujesz tylko w oknie Ubuntu, gdy xtool sam o nie zapyta. Nigdzie indziej go nie podawaj.

**Dlaczego w Windowsie nie używamy usbipd.** Przekazywanie USB do WSL przez usbipd w wielu przypadkach kończy się tym, że `xtool dev` staje na `[Connecting] 100%` i nic się nie dzieje. Jest to znany problem opisany w zgłoszeniach xtool, a autor zaleca obejście przez aplikację Apple Devices z przekierowaniem portu. Ta instrukcja opisuje właśnie to obejście, bo ono zadziałało.

## Windows, część 1: konfiguracja od zera

Po tych 13 krokach aplikacja jest na telefonie. Całość zajmuje ok. 1-2 godzin, głównie przez pobieranie i rozpakowywanie Xcode. Kroków nie pomijaj i nie zmieniaj ich kolejności.

### Krok 1. Zainstaluj WSL (Ubuntu w Windowsie)

**\[PowerShell, administrator\]**

```
wsl --install
```

1. Po zakończeniu zrestartuj komputer.
2. Po restarcie otworzy się okno Ubuntu (jeśli nie, Start, wpisz Ubuntu). Ustaw login i hasło. Hasła nie widać podczas pisania. Zapamiętaj login, bo będzie potrzebny w ścieżkach (w tej instrukcji zapisany jako TWOJLOGIN).
3. Gdy zobaczysz `TWOJLOGIN@KOMPUTER:~$`, jesteś w Ubuntu.

### Krok 2. Zainstaluj Apple Devices

**\[Microsoft Store\]** W Start wpisz Microsoft Store, w wyszukiwarce wpisz Apple Devices (wydawca Apple Inc.) i kliknij Pobierz. Jeśli go nie ma, zainstaluj ze sklepu iTunes. Ten program jest potrzebny tylko po to, żeby Windows udostępnił telefon Ubuntu. Nie musisz z niego korzystać.

Podłącz iPhone'a kablem, odblokuj go i kliknij Ufaj. Uruchom Apple Devices i sprawdź, że widać w nim Twój telefon.

### Krok 3. Pobierz Xcode.xip

**\[Przeglądarka\]**

1. Wejdź na `https://developer.apple.com/download/all/?q=Xcode` i zaloguj się swoim Apple ID. Wystarczy darmowe konto, bez płatnego członkostwa. Apple może poprosić o kod weryfikacyjny i o zaakceptowanie umowy dla deweloperów.
2. Pobierz plik Xcode z rozszerzeniem `.xip` (np. `Xcode_27.2.xip`). Nie wybieraj Command Line Tools ani wersji beta czy Release Candidate, jeśli jest zwykła.
3. Plik zostaw w folderze Pobrane. Nie otwieraj go, nie klikaj w niego dwukrotnie i nie pobieraj przez `curl`. Rozpakuje go sam xtool.

Xcode z App Store jest tylko na Maca, ale ten plik `.xip` pobierzesz z dowolnego komputera zwykłą przeglądarką.

### Krok 4. Zainstaluj potrzebne programy w Ubuntu

**\[Ubuntu\]**

```
sudo apt update
sudo apt install -y curl git zip unzip usbmuxd libimobiledevice-utils netcat-openbsd
```

Poprosi o hasło do Ubuntu (nie widać go przy pisaniu). Program `zip` jest niezbędny: bez niego xtool kończy się błędem `executable zip is not found`.

### Krok 5. Zainstaluj Swift

**\[Ubuntu\]** Wklejaj po jednej komendzie i czekaj na znak `$`.

```
curl -O https://download.swift.org/swiftly/linux/swiftly-$(uname -m).tar.gz
tar zxf swiftly-$(uname -m).tar.gz
./swiftly init --quiet-shell-followup
```

Na pytania instalatora odpowiadaj `y` i Enter. Instalacja trwa kilka minut. Potem:

```
. $HOME/.local/share/swiftly/env.sh
hash -r
swift --version
```

Ma wypisać wersję 6.4 lub wyższą.

**Co oznaczają szczegóły z tych komend:**

- W `curl -O` jest duża litera O, a nie zero.
- Polecenie `. $HOME/...` zaczyna się od samej kropki, po której jest spacja. To komenda "wczytaj ustawienia z tego pliku". Bez niej Ubuntu nie wie, że Swift jest zainstalowany.
- Używaj `$HOME` zamiast tyldy `~`. W tej komendzie tylda nie rozwija się poprawnie, a `$HOME` działa zawsze.
- Jeśli `swift --version` mówi, że nie ma takiej komendy, wpisz `swiftly install latest`, potem `swiftly use latest`.
- Jeśli wypisze błąd `libncurses.so.6: cannot open shared object file`, doinstaluj biblioteki i sprawdź ponownie (jeśli któryś pakiet nie istnieje, pomiń go):

```
sudo apt install -y libncurses-dev libcurl4-openssl-dev libxml2-dev libedit-dev libsqlite3-dev libpython3-dev libz3-dev pkg-config tzdata zlib1g-dev binutils gnupg2 libc6-dev libgcc-13-dev libstdc++-13-dev unzip
swift --version
```

### Krok 6. Zainstaluj xtool

**\[Ubuntu\]**

```
cd $HOME
curl -fL "https://github.com/xtool-org/xtool/releases/latest/download/xtool-$(uname -m).AppImage" -o xtool-new
chmod +x xtool-new
sudo mv xtool-new /usr/local/bin/xtool
xtool --version
```

Ma wypisać numer wersji. Ten sam blok służy do aktualizacji xtool.

Nie rób tego w folderze projektu `ESPCamDrive`. Xtool tworzy tam własny podfolder o nazwie `xtool`, więc zapis pliku pod tą samą nazwą kończy się błędem `client return error on write`. Dlatego pobieramy do folderu domowego i pod nazwą `xtool-new`.

### Krok 7. Skopiuj Xcode.xip do Ubuntu

**\[Ubuntu\]** wpisz `explorer.exe .` (z kropką). Otworzy się Eksplorator z folderem domowym Ubuntu. **\[Eksplorator\]** skopiuj do niego plik `.xip` z folderu Pobrane. Kopiowanie kilkunastu GB trwa kilka minut. Możesz też wpisać w pasku adresu Eksploratora `\\wsl.localhost\Ubuntu\home\TWOJLOGIN`.

Sprawdź, czy plik jest na miejscu:

```
ls -lh $HOME/*.xip
```

Ważne: komendy wpisuj w oknie, w którym znak zachęty wygląda jak `TWOJLOGIN@KOMPUTER:~$`. Okno z innym znakiem zachęty (np. `KOMPUTER:/mnt/host/c/Users/...#`) to inny system Linux działający w tle (np. od Dockera). Tam xtool nie zadziała i nie widzi plików Ubuntu.

### Krok 8. Skonfiguruj xtool

**\[Ubuntu\]**

```
xtool setup
```

1. Wybór trybu logowania: wpisz `1` (Password, działa z każdym Apple ID). Opcja `0` wymaga płatnego konta developera.
2. Podaj Apple ID (adres e-mail konta), hasło i kod weryfikacyjny, jeśli Apple go wyśle. Wpisuj je tylko w tym oknie.
3. Gdy zapyta o `Path to Xcode.xip`, wpisz pełną ścieżkę z nazwą pliku, np. `/home/TWOJLOGIN/Xcode_27.2.xip`. Ścieżki w stylu `C:\Users\...` ani `/mnt/c/...` nie używaj.
4. Poczekaj. Rozpakowywanie trwa nawet kilkadziesiąt minut, a pasek postępu może długo stać w miejscu. Nie zamykaj okna.
5. Sprawdź wynik: `swift sdk list`. Na liście ma być `darwin`.

Po udanej konfiguracji możesz usunąć plik z dysku, żeby odzyskać miejsce: `rm $HOME/Xcode_27.2.xip`.

**Zapasowe Apple ID.** Apple ID to po prostu adres e-mail, więc nie ma go gdzie "znaleźć", tylko trzeba je założyć. Wejdź na `https://account.apple.com`, kliknij Utwórz konto Apple i użyj adresu e-mail, którego nie używa żadne inne Apple ID (np. nowy na Gmailu). Numer telefonu do weryfikacji może być Twój zwykły. To drugie konto służy tylko do podpisywania aplikacji. Telefon może zostać zalogowany na swoim normalnym koncie. Możesz też użyć swojego głównego Apple ID, ale na własne ryzyko, bo xtool korzysta z nieoficjalnych interfejsów Apple.

### Krok 9. Połącz telefon z Ubuntu

To najtrudniejszy krok. Telefon zostaje w Windowsie, a Ubuntu łączy się z nim przez sieć, za pośrednictwem Apple Devices.

**9a. Sprawdź, że telefon jest po stronie Windowsa**

**\[PowerShell, administrator\]** Wpisz `usbipd list` (jeśli to polecenie nie istnieje, pomiń ten punkt). Przy iPhonie nie może być Attached ani Shared. Jeśli jest, wpisz `usbipd detach --busid NUMER` i `usbipd unbind --busid NUMER`, gdzie NUMER to wartość z kolumny BUSID w tym samym wierszu. Potem odepnij i podepnij kabel, odblokuj telefon i kliknij Ufaj.

Sprawdź, czy Apple Devices nasłuchuje:

```
netstat -ano | findstr ":27015" | findstr LISTENING
```

Ma wypisać linijkę z `127.0.0.1:27015` i słowem LISTENING. Jeśli nic nie wypisze, uruchom Apple Devices i upewnij się, że widzi telefon.

**9b. Sprawdź adres sieci WSL**

**\[PowerShell, administrator\]** Okno Ubuntu musi być otwarte, inaczej odpowiednia karta nie istnieje.

```
ipconfig
```

Znajdź sekcję z WSL w nazwie. U niektórych jest to `vEthernet (WSL)`, u innych `vEthernet (WSL (Hyper-V firewall))`. Zapisz sobie dokładną nazwę karty i jej adres IPv4 (np. `172.21.48.1`). W dalszych komendach nazwa karty to NAZWAKARTY, a adres to TWOJ\_ADRES\_IPV4.

**9c. Otwórz zaporę (jednorazowo)**

**\[PowerShell, administrator\]** W miejsce `vEthernet (WSL)` wpisz nazwę swojej karty z 9b:

```
New-NetFirewallRule -DisplayName "WSL" -Direction Inbound -InterfaceAlias "vEthernet (WSL)" -Action Allow
New-NetFirewallRule -DisplayName "usbmuxd 27015" -Direction Inbound -Protocol TCP -LocalPort 27015 -Action Allow
```

Na Windows 11 dodaj jeszcze tę komendę. Na Windows 10 zwróci błąd, który możesz zignorować:

```
Set-NetFirewallHyperVVMSetting -Name '{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}' -DefaultInboundAction Allow
```

**9d. Włącz usługę przekierowania portów**

**\[PowerShell, administrator\]**

```
sc.exe config iphlpsvc start= auto
sc.exe start iphlpsvc
```

Po `start=` jest spacja i musi tam być. Jeśli druga komenda napisze, że usługa już działa, to dobrze. Komenda `Set-Service ... -StartupType` w starszym PowerShellu zgłasza błąd `parameter cannot be found`, dlatego używamy `sc.exe`.

**9e. Ustaw przekierowanie portu**

**\[PowerShell, administrator\]** Podstaw swój adres IPv4 z 9b w miejsce TWOJ\_ADRES\_IPV4 (w obu komendach):

```
netsh interface portproxy delete v4tov4 listenport=27015 listenaddress=TWOJ_ADRES_IPV4
netsh interface portproxy add v4tov4 listenport=27015 listenaddress=TWOJ_ADRES_IPV4 connectport=27015 connectaddress=127.0.0.1
```

Pierwsza komenda może napisać, że nie ma takiej reguły. To nic nie szkodzi. Sprawdź:

```
netstat -ano | findstr ":27015" | findstr LISTENING
```

Mają być dwie linijki: `127.0.0.1:27015` i `TWOJ_ADRES_IPV4:27015`. Jeśli jest tylko pierwsza, przekierowanie nie działa i `nc` w kroku 9f będzie wisiał.

**9f. Sprawdź połączenie z Ubuntu**

**\[Ubuntu\]** wszystkie trzy komendy w jednym oknie:

```
export USBMUXD_SOCKET_ADDRESS="$(ip route list default | awk '{print $3}'):27015"
nc -zv $(ip route list default | awk '{print $3}') 27015
ideviceinfo
```

- Druga komenda ma wypisać `succeeded` (albo `open`). Jeśli wisi, wróć do 9e i 9c. Przerwij ją klawiszami Ctrl+C.
- Trzecia ma wypisać dużo linijek z danymi telefonu. Jeśli pisze `No device found`, sprawdź 9a: telefon nie może być przekazany do Ubuntu (`Shared`, `Attached`) i musi być widoczny w Apple Devices.
- Pierwszą komendę (`export`) trzeba wpisać w każdym nowym oknie Ubuntu, w którym będziesz używać xtool.
- Jeśli adres z `ip route list default` nie zaczyna się od `172.` (np. `192.168...`), masz tryb sieciowy mirrored. Wtedy pomiń 9b, 9c i 9e i użyj: `export USBMUXD_SOCKET_ADDRESS="127.0.0.1:27015"`.

### Krok 10. Wgraj projekt

**\[Ubuntu\]** `explorer.exe .`, a potem **\[Eksplorator\]** wrzuć do otwartego folderu plik `ESPCamDrive.zip`. Znowu **\[Ubuntu\]**:

```
cd $HOME
unzip -o ESPCamDrive.zip
cd ESPCamDrive
ls
```

W wyniku mają być m.in. `Package.swift`, `xtool.yml` i folder `Sources`. Jeśli ich nie widzisz, a jest tylko drugi folder `ESPCamDrive`, wejdź do niego: `cd ESPCamDrive`.

Ustaw Client ID z Google Cloud. **\[Eksplorator\]** otwórz w Notatniku plik `ESPCamDrive\Sources\ESPCamDrive\Config.swift` i zamień `WKLEJ_TUTAJ_CLIENT_ID.apps.googleusercontent.com` na swój Client ID (zostaw cudzysłowy). Sprawdź w **\[Ubuntu\]**:

```
grep googleClientID $HOME/ESPCamDrive/Sources/ESPCamDrive/Config.swift
```

Ma pokazać Twój Client ID, a nie napis WKLEJ\_TUTAJ. W Google Cloud klient ma typ iOS, a jego Bundle ID musi być taki sam jak `bundleID` w pliku `xtool.yml` (domyślnie `com.ola.espcamdrive`).

### Krok 11. Włącz tryb dewelopera na telefonie

**\[Telefon\]** Ustawienia, Prywatność i ochrona, Tryb dewelopera. Włącz, zrestartuj telefon i po restarcie potwierdź kodem. Ta opcja pojawia się dopiero po pierwszej próbie instalacji aplikacji z komputera. Jeśli jej nie widzisz, wykonaj najpierw krok 12, a potem wróć tutaj i powtórz krok 12.

Po restarcie telefonu odblokuj go i upewnij się, że Apple Devices go widzi. Potem w oknie Ubuntu wpisz ponownie komendę `export` z kroku 9f.

### Krok 12. Zbuduj i zainstaluj aplikację

**\[Ubuntu\]**

```
export USBMUXD_SOCKET_ADDRESS="$(ip route list default | awk '{print $3}'):27015"
ideviceinfo
cd $HOME/ESPCamDrive
xtool dev
```

Telefon musi być cały czas odblokowany. Zobaczysz kolejne etapy z paskami postępu: Planning, Building, Unpacking app, Logging in, Preparing device, Provisioning, Signing, Packaging, Connecting, Installing i Verifying. Pierwsze budowanie trwa dłużej, a xtool może poprosić o kod weryfikacyjny Apple.

Jeśli stoi na `[Connecting] 100%` dłużej niż kilka minut, przerwij Ctrl+C i wróć do kroku 9f (to znaczy, że Ubuntu nie widzi telefonu przez przekierowanie).

### Krok 13. Zaufaj profilowi i uruchom aplikację

**\[Telefon\]** Ustawienia, Ogólne, VPN i zarządzanie urządzeniem. W sekcji Aplikacja dewelopera kliknij profil `Apple Development: ...` z adresem e-mail Apple ID użytego w xtool (to może być inny adres niż konto na telefonie, to normalne). Kliknij Ufaj i potwierdź. Potem otwórz aplikację ESP Cam Drive. Przy pierwszym uruchomieniu zezwól na dostęp do sieci lokalnej (bez tego nie połączy się z kamerą) i na lokalizację.

Przy darmowym koncie Apple aplikacja przestaje działać po 7 dniach. Wtedy wgrywasz ją ponownie według części 2.

## Windows, część 2: ponowne wgranie aplikacji

Do ponownego wgrania nie powtarzasz instalacji WSL, Swifta, xtool ani `xtool setup`. Wystarczą trzy bloki: przygotowanie (A), nowy kod (B, tylko gdy kod się zmienił) i instalacja (C).

| Sytuacja | Które bloki |
| --- | --- |
| Aplikacja przestała działać po 7 dniach, kod bez zmian | A, C |
| Masz nową wersję kodu (nowy zip) | A, B, C |
| Po restarcie komputera nic się nie łączy | D, potem A, C |

### A. Przygotowanie (za każdym razem)

1. **\[Telefon\]** Podłącz iPhone'a kablem, odblokuj i w razie pytania kliknij Ufaj. W aplikacji Apple Devices telefon ma być widoczny.
2. **\[Ubuntu\]** Otwórz okno Ubuntu (Start, wpisz Ubuntu). Adres sieci WSL istnieje tylko wtedy, gdy WSL działa.
3. **\[PowerShell, administrator\]** Sprawdź, czy oba nasłuchiwania są aktywne:

```
netstat -ano | findstr ":27015" | findstr LISTENING
```

Mają być dwie linijki: `127.0.0.1:27015` i drugą z adresem Twojej sieci WSL (np. `172.21.48.1:27015`). Jeśli brakuje drugiej, przejdź do bloku D.

4. **\[Ubuntu\]** Wpisz w jednym oknie:

```
export USBMUXD_SOCKET_ADDRESS="$(ip route list default | awk '{print $3}'):27015"
ideviceinfo
```

`ideviceinfo` ma wypisać dane telefonu. Komendę `export` trzeba wpisać w każdym nowym oknie Ubuntu, bo zmienna działa tylko w oknie, w którym ją ustawiono.

### B. Nowa wersja kodu (tylko gdy masz nowy zip)

1. **\[Ubuntu\]** wpisz `explorer.exe .`, a potem **\[Eksplorator\]** wrzuć nowy `ESPCamDrive.zip` do otwartego folderu (zastąp stary).
2. **\[Ubuntu\]** Rozpakuj z nadpisaniem.

Jeśli Client ID jest już wpisany w pliku `Config.swift` wewnątrz nowego zipa, wystarczy:

```
cd $HOME
unzip -o ESPCamDrive.zip
grep googleClientID ESPCamDrive/Sources/ESPCamDrive/Config.swift
```

Jeśli w nowym zipie Client ID jest pusty (napis WKLEJ\_TUTAJ), zapisz go najpierw na bok i przywróć po rozpakowaniu:

```
cd $HOME
cp ESPCamDrive/Sources/ESPCamDrive/Config.swift $HOME/Config.swift.bak
unzip -o ESPCamDrive.zip
cp $HOME/Config.swift.bak ESPCamDrive/Sources/ESPCamDrive/Config.swift
grep googleClientID ESPCamDrive/Sources/ESPCamDrive/Config.swift
```

Polecenie `grep` ma pokazać Twój Client ID. Plik `Config.swift` musi leżeć w zipie w układzie `ESPCamDrive/Sources/ESPCamDrive/Config.swift`. Przy innym układzie `unzip` położy go w innym miejscu, a stary zostanie nietknięty.

3. Nie zmieniaj `bundleID` w pliku `xtool.yml`. Dzięki temu nowa wersja nadpisuje starą, a identyfikator zgadza się z Google Cloud.
4. Jeśli nowa wersja usuwa jakieś pliki źródłowe, `unzip -o` ich sam nie skasuje. W takim przypadku usuń najpierw stary folder (`rm -r $HOME/ESPCamDrive`), rozpakuj zip i wpisz Client ID od nowa.

### C. Instalacja na telefonie

**\[Ubuntu\]** (w tym samym oknie, w którym zrobiłeś `export` w bloku A):

```
cd $HOME/ESPCamDrive
xtool dev
```

Telefon ma być cały czas odblokowany. Przejdą kolejne etapy aż do Installing i Verifying, a aplikacja uruchomi się na telefonie. Nowa wersja nadpisuje poprzednią i ma tę samą ikonę. Jeśli iOS poprosi o zaufanie profilowi, zrób to jak w kroku 13 części 1 (Ustawienia, Ogólne, VPN i zarządzanie urządzeniem).

### D. Po restarcie komputera

Adres sieci WSL potrafi się zmienić po restarcie, a wtedy stare przekierowanie wskazuje w próżnię.

1. **\[Ubuntu\]** Wpisz `ip route list default` i zapisz adres po słowie `via`.
2. **\[PowerShell, administrator\]** Wpisz `ipconfig` i znajdź IPv4 w sekcji `vEthernet (WSL)`. To powinien być ten sam adres co w punkcie 1.
3. Zobacz, co jest ustawione, i podmień na nowy adres:

```
netsh interface portproxy show all
netsh interface portproxy delete v4tov4 listenport=27015 listenaddress=STARY_ADRES
netsh interface portproxy add v4tov4 listenport=27015 listenaddress=NOWY_ADRES connectport=27015 connectaddress=127.0.0.1
sc.exe start iphlpsvc
```

STARY\_ADRES to adres z kolumny Listen on w wyniku pierwszej komendy, a NOWY\_ADRES to adres z punktu 2. Jeśli ostatnia komenda napisze, że usługa już działa, to dobrze. Potem wróć do bloku A.

### Gdy coś nie działa

| Objaw | Przyczyna | Co zrobić |
| --- | --- | --- |
| `ideviceinfo` pisze No device found | Telefon jest przekazany do Ubuntu przez usbipd (Shared lub Attached) albo Apple Devices go nie widzi | W PowerShellu `usbipd detach` i `usbipd unbind`, potem odepnij i podepnij kabel |
| `ideviceinfo` nic nie wypisuje albo wisi | Brak zmiennej `export` w tym oknie albo brak przekierowania | Wpisz `export ...` z bloku A, potem sprawdź blok D |
| `nc -zv ...` wisi | Przekierowanie portu lub zapora | Blok D oraz krok 9c z części 1 |
| `xtool dev` stoi na `[Connecting] 100%` | Ubuntu nie widzi telefonu | Przerwij Ctrl+C, wróć do bloku A i sprawdź `ideviceinfo` |
| iPhone pokazuje niezaufanego dewelopera | Profil nie jest zaufany | Krok 13 z części 1 |
| Aplikacja przestała się otwierać po tygodniu | Wygasła ważność podpisu (konto darmowe) | Bloki A i C |

## Linux, część 1: konfiguracja od zera

Na Linuxie jest prościej niż na Windowsie: telefon łączy się wprost przez usługę usbmuxd, więc nie potrzebujesz WSL, aplikacji Apple Devices, przekierowania portów ani zmiennej `USBMUXD_SOCKET_ADDRESS`. Komendy poniżej są dla Ubuntu i Debiana (menedżer pakietów `apt`). Na innych dystrybucjach nazwy pakietów mogą się różnić.

Uwaga: tę wersję oparłem na oficjalnej dokumentacji xtool. W naszej rozmowie przechodziliśmy przez wersję Windows, więc Linuxa nie testowaliśmy na żywo. Szczegóły podstaw (kopiowanie, wklejanie, wielkie litery jako miejsca do podmiany) znajdziesz w sekcji Zanim zaczniesz. Etykieta **\[Terminal\]** oznacza zwykłe okno terminala, a **\[Telefon\]** ekran iPhone'a.

### Krok 1. Zainstaluj potrzebne pakiety

**\[Terminal\]**

```
sudo apt update
sudo apt install -y curl git zip unzip usbmuxd libimobiledevice-utils libfuse2
```

Program `zip` jest niezbędny: bez niego xtool kończy się błędem `executable zip is not found`. Pakiet `libfuse2` jest potrzebny do uruchomienia pliku AppImage. Jeśli `apt` nie zna takiego pakietu (nowsze Ubuntu), użyj nazwy `libfuse2t64`. Po zainstalowaniu `usbmuxd` odepnij i podepnij kabel telefonu raz, żeby system uruchomił usługę.

### Krok 2. Zainstaluj Swift

**\[Terminal\]**

```
curl -O https://download.swift.org/swiftly/linux/swiftly-$(uname -m).tar.gz
tar zxf swiftly-$(uname -m).tar.gz
./swiftly init --quiet-shell-followup
. $HOME/.local/share/swiftly/env.sh
hash -r
swift --version
```

Na pytania instalatora odpowiadaj `y`. Ma wypisać wersję 6.4 lub wyższą. Szczegóły:

- W `curl -O` jest duża litera O, a nie zero.
- Polecenie `. $HOME/...` zaczyna się od samej kropki, po której jest spacja. Bez niej terminal nie wie, że Swift jest zainstalowany.
- Używaj `$HOME` zamiast tyldy `~`.
- Jeśli `swift --version` mówi, że nie ma takiej komendy, wpisz `swiftly install latest`, potem `swiftly use latest`.
- Jeśli pojawi się błąd `libncurses.so.6: cannot open shared object file`, doinstaluj biblioteki (pakiet, którego nie ma w Twojej wersji, pomiń):

```
sudo apt install -y libncurses-dev libcurl4-openssl-dev libxml2-dev libedit-dev libsqlite3-dev libpython3-dev libz3-dev pkg-config tzdata zlib1g-dev binutils gnupg2 libc6-dev libgcc-13-dev libstdc++-13-dev unzip
```

### Krok 3. Zainstaluj xtool

**\[Terminal\]**

```
cd $HOME
curl -fL "https://github.com/xtool-org/xtool/releases/latest/download/xtool-$(uname -m).AppImage" -o xtool-new
chmod +x xtool-new
sudo mv xtool-new /usr/local/bin/xtool
xtool --version
```

Nie rób tego w folderze projektu `ESPCamDrive`: xtool tworzy tam podfolder o nazwie `xtool`, więc zapis pliku pod tą samą nazwą się nie uda. Ten sam blok służy do aktualizacji xtool.

### Krok 4. Pobierz Xcode.xip

**\[Przeglądarka\]**

1. Wejdź na `https://developer.apple.com/download/all/?q=Xcode` i zaloguj się swoim Apple ID. Wystarczy darmowe konto. Apple może poprosić o kod weryfikacyjny i zaakceptowanie umowy dla deweloperów.
2. Pobierz plik Xcode z rozszerzeniem `.xip` (np. `Xcode_27.2.xip`), a nie Command Line Tools ani wersję beta.
3. Nie otwieraj go i nie pobieraj przez `curl`. Rozpakuje go sam xtool.

Xcode z App Store jest tylko na Maca, ale ten plik `.xip` pobierzesz z dowolnego komputera.

Plik ląduje zwykle w folderze `Downloads`. Jego pełna ścieżka to np. `/home/TWOJLOGIN/Downloads/Xcode_27.2.xip`. Sprawdź nazwę: **\[Terminal\]** `ls -lh $HOME/Downloads/*.xip`.

### Krok 5. Skonfiguruj xtool

**\[Terminal\]**

```
xtool setup
```

1. Wybór trybu logowania: wpisz `1` (Password, działa z każdym Apple ID). Opcja `0` wymaga płatnego konta developera.
2. Podaj Apple ID (adres e-mail konta), hasło i kod weryfikacyjny, jeśli Apple go wyśle. Wpisuj je tylko w terminalu.
3. Gdy zapyta o `Path to Xcode.xip`, wpisz pełną ścieżkę z nazwą pliku, np. `/home/TWOJLOGIN/Downloads/Xcode_27.2.xip`.
4. Poczekaj. Rozpakowywanie trwa nawet kilkadziesiąt minut. Nie zamykaj terminala.
5. Sprawdź: `swift sdk list`. Na liście ma być `darwin`.

Po udanej konfiguracji możesz usunąć pobrany plik `.xip`, żeby odzyskać miejsce.

**Zapasowe Apple ID.** Apple ID to adres e-mail, więc trzeba je założyć, a nie znaleźć. Wejdź na `https://account.apple.com`, kliknij Utwórz konto Apple i użyj adresu e-mail, którego nie używa żadne inne Apple ID. Numer telefonu do weryfikacji może być Twój zwykły. To konto służy tylko do podpisywania aplikacji, a telefon może zostać zalogowany na swoim normalnym koncie. Możesz też użyć swojego głównego Apple ID, na własne ryzyko, bo xtool używa nieoficjalnych interfejsów Apple.

### Krok 6. Sparuj telefon

**\[Telefon\]** Podłącz iPhone'a kablem, odblokuj go i kliknij Ufaj (wpisz kod). **\[Terminal\]**

```
idevicepair pair
idevicepair validate
ideviceinfo
```

`validate` ma napisać `SUCCESS: Validated pairing`, a `ideviceinfo` wypisać dane telefonu. Jeśli widzisz błąd `could not connect to lockdownd, error code -8` albo `No device found`: odblokuj telefon, kliknij Ufaj jeszcze raz, odepnij i podepnij kabel (najlepiej do portu bez huba) i uruchom usługę ponownie: `sudo systemctl restart usbmuxd`. Jeśli ta komenda nie istnieje, użyj `sudo killall usbmuxd` i `sudo usbmuxd`.

### Krok 7. Wgraj projekt

**\[Terminal\]** Podstaw ścieżkę do pobranego zipa:

```
cd $HOME
unzip -o $HOME/Downloads/ESPCamDrive.zip
cd ESPCamDrive
ls
```

W wyniku mają być m.in. `Package.swift`, `xtool.yml` i folder `Sources`. Jeśli ich nie widzisz, a jest tylko drugi folder `ESPCamDrive`, wejdź do niego: `cd ESPCamDrive`.

Ustaw Client ID z Google Cloud. Otwórz plik w edytorze:

```
nano $HOME/ESPCamDrive/Sources/ESPCamDrive/Config.swift
```

Zamień `WKLEJ_TUTAJ_CLIENT_ID.apps.googleusercontent.com` na swój Client ID (zostaw cudzysłowy). Zapisz klawiszami Ctrl+O i Enter, wyjdź przez Ctrl+X. Możesz też użyć dowolnego graficznego edytora. Sprawdź:

```
grep googleClientID $HOME/ESPCamDrive/Sources/ESPCamDrive/Config.swift
```

Ma pokazać Twój Client ID, a nie napis WKLEJ\_TUTAJ. W Google Cloud klient ma typ iOS, a jego Bundle ID musi być taki sam jak `bundleID` w pliku `xtool.yml` (domyślnie `com.ola.espcamdrive`).

### Krok 8. Włącz tryb dewelopera na telefonie

**\[Telefon\]** Ustawienia, Prywatność i ochrona, Tryb dewelopera. Włącz, zrestartuj telefon i po restarcie potwierdź kodem. Ta opcja pojawia się dopiero po pierwszej próbie instalacji aplikacji z komputera. Jeśli jej nie widzisz, wykonaj najpierw krok 9, a potem wróć tutaj i powtórz krok 9.

### Krok 9. Zbuduj i zainstaluj aplikację

**\[Terminal\]**

```
cd $HOME/ESPCamDrive
xtool dev
```

Telefon musi być odblokowany. Zobaczysz kolejne etapy z paskami postępu: Planning, Building, Unpacking app, Logging in, Preparing device, Provisioning, Signing, Packaging, Connecting, Installing i Verifying. Pierwsze budowanie trwa dłużej, a xtool może poprosić o kod weryfikacyjny Apple.

Jeśli stoi na `[Connecting] 100%` dłużej niż kilka minut, przerwij Ctrl+C i wróć do kroku 6: to znaczy, że komputer nie widzi telefonu.

### Krok 10. Zaufaj profilowi i uruchom aplikację

**\[Telefon\]** Ustawienia, Ogólne, VPN i zarządzanie urządzeniem. W sekcji Aplikacja dewelopera kliknij profil `Apple Development: ...` z adresem e-mail Apple ID użytego w xtool (to może być inny adres niż konto na telefonie, to normalne). Kliknij Ufaj i potwierdź. Potem otwórz aplikację ESP Cam Drive. Przy pierwszym uruchomieniu zezwól na dostęp do sieci lokalnej (bez tego nie połączy się z kamerą) i na lokalizację.

Przy darmowym koncie Apple aplikacja przestaje działać po 7 dniach. Wtedy wgrywasz ją ponownie według części 2.

## Linux, część 2: ponowne wgranie aplikacji

Do ponownego wgrania wystarczą trzy bloki: przygotowanie (A), nowy kod (B, tylko gdy kod się zmienił) i instalacja (C). Nie powtarzasz instalacji Swifta, xtool ani `xtool setup`.

| Sytuacja | Które bloki |
| --- | --- |
| Aplikacja przestała działać po 7 dniach, kod bez zmian | A, C |
| Masz nową wersję kodu (nowy zip) | A, B, C |

### A. Przygotowanie (za każdym razem)

1. **\[Telefon\]** Podłącz iPhone'a kablem, odblokuj go i w razie pytania kliknij Ufaj.
2. **\[Terminal\]** Sprawdź, czy komputer widzi telefon:

```
ideviceinfo
```

Ma wypisać dane telefonu. Jeśli pisze `No device found` albo `error code -8`, odblokuj telefon, odepnij i podepnij kabel, wpisz `idevicepair pair`, kliknij Ufaj na telefonie i w razie potrzeby uruchom usługę ponownie: `sudo systemctl restart usbmuxd` (albo `sudo killall usbmuxd`, potem `sudo usbmuxd`).

### B. Nowa wersja kodu (tylko gdy masz nowy zip)

**\[Terminal\]** Jeśli Client ID jest już wpisany w pliku `Config.swift` wewnątrz nowego zipa, wystarczy rozpakować z nadpisaniem i sprawdzić:

```
cd $HOME
unzip -o $HOME/Downloads/ESPCamDrive.zip
grep googleClientID ESPCamDrive/Sources/ESPCamDrive/Config.swift
```

Jeśli w nowym zipie Client ID jest pusty (napis WKLEJ\_TUTAJ), zapisz go najpierw na bok i przywróć po rozpakowaniu:

```
cd $HOME
cp ESPCamDrive/Sources/ESPCamDrive/Config.swift $HOME/Config.swift.bak
unzip -o $HOME/Downloads/ESPCamDrive.zip
cp $HOME/Config.swift.bak ESPCamDrive/Sources/ESPCamDrive/Config.swift
grep googleClientID ESPCamDrive/Sources/ESPCamDrive/Config.swift
```

Polecenie `grep` ma pokazać Twój Client ID. Plik `Config.swift` musi leżeć w zipie w układzie `ESPCamDrive/Sources/ESPCamDrive/Config.swift`, inaczej `unzip` położy go w innym miejscu, a stary zostanie nietknięty. Nie zmieniaj `bundleID` w `xtool.yml`, żeby nowa wersja nadpisała starą i zgadzała się z Google Cloud. Jeśli nowa wersja usuwa pliki źródłowe, `unzip -o` ich nie skasuje. Wtedy usuń najpierw stary folder (`rm -r $HOME/ESPCamDrive`), rozpakuj zip i wpisz Client ID od nowa.

### C. Instalacja na telefonie

**\[Terminal\]**

```
cd $HOME/ESPCamDrive
xtool dev
```

Telefon ma być cały czas odblokowany. Przejdą kolejne etapy aż do Installing i Verifying, a aplikacja uruchomi się na telefonie. Nowa wersja nadpisuje poprzednią. Jeśli iOS poprosi o zaufanie profilowi, zrób to jak w kroku 10 części 1.

Po większej aktualizacji systemu albo Xcode warto zaktualizować xtool blokiem z kroku 3 części 1.

### Gdy coś nie działa

| Objaw | Przyczyna | Co zrobić |
| --- | --- | --- |
| `ideviceinfo` pisze No device found albo error code -8 | Telefon zablokowany, niesparowany albo usługa usbmuxd nie działa | Odblokuj, Ufaj, `idevicepair pair`, restart usbmuxd, odepnij i podepnij kabel |
| `xtool dev` stoi na `[Connecting] 100%` | Komputer nie widzi telefonu | Przerwij Ctrl+C, sprawdź `ideviceinfo` |
| `executable zip is not found` | Brak programu zip | `sudo apt install -y zip` |
| xtool wypisuje `client return error on write` przy aktualizacji | Zapis w folderze projektu, gdzie istnieje podfolder `xtool` | Aktualizuj z `cd $HOME` i nazwą pliku `xtool-new` |
| iPhone pokazuje niezaufanego dewelopera | Profil nie jest zaufany | Krok 10 z części 1 |
| Aplikacja przestała się otwierać po tygodniu | Wygasła ważność podpisu (konto darmowe) | Bloki A i C |

## Najczęstsze pytania

**Czy Apple ID to e-mail i skąd wziąć zapasowe?** Tak, Apple ID to adres e-mail. Zapasowe trzeba założyć na `account.apple.com`, z nowym adresem e-mail. Jest to zalecane, ale nie obowiązkowe.

**Czy Apple ID w xtool może być inne niż na telefonie?** Tak, nie przeszkadza to w instalacji. Na telefonie zobaczysz po prostu profil z tym drugim adresem i zaufasz mu w Ustawieniach.

**Dlaczego xtool stoi na `[Connecting] 100%` i nic się nie dzieje?** Najczęściej komputer nie widzi telefonu. Na Windowsie przekazywanie USB przez usbipd potrafi powodować dokładnie to zawieszenie, dlatego używamy Apple Devices i przekierowania portu (krok 9). Na Linuxie sprawdź `ideviceinfo` i parowanie. Jeśli `ideviceinfo` działa, a xtool dalej stoi, spróbuj zalogować się ponownie: `xtool auth login`.

**Co powinno być po Connecting?** Kolejne paski: Installing i Verifying, a potem aplikacja uruchamia się na telefonie.

**Jak włączyć tryb dewelopera na telefonie?** Ustawienia, Prywatność i ochrona, Tryb dewelopera. Opcja pojawia się dopiero po pierwszej próbie instalacji z komputera. Po włączeniu telefon się restartuje, a po restarcie trzeba odblokować go i potwierdzić kodem.

**Czy po restarcie telefonu coś trzeba robić w komputerze?** Na Windowsie: odblokować telefon, sprawdzić go w Apple Devices i wpisać ponownie `export USBMUXD_SOCKET_ADDRESS=...` w oknie Ubuntu. Na Linuxie: odblokować i w razie potrzeby `idevicepair pair`.

### Źródła

- [Dokumentacja instalacji xtool (Linux i WSL)](https://github.com/xtool-org/xtool/blob/main/Documentation/xtool.docc/Installation-Linux.md): wymagania (Swift, usbmuxd, Xcode.xip), instalacja i `xtool setup`.
- [Zgłoszenie xtool nr 19: xtool dev stuck on Connecting step](https://github.com/xtool-org/xtool/issues/19): zawieszenie na Connecting przy WSL z usbipd i obejście przez iTunes lub Apple Devices z przekierowaniem portu 27015.
