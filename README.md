SKRIPTIT SAATTAA FREEZATA (voi johtua gpu pluginista)

mlm skripti
älä käytä yläkerran deposit hopperia
lockaa hammer inventoryyn bankin asetuksista



wc plugin toimii normal,oak,yew,magic puilla

normal logs only power chop
oak varrock westissä, bank option
yew/magic wc guildissa, bank option



agility skripti toimii draynor,varrock,canifis,falador,camelot



# RuneLite - Asennusohje (Komentokehote / CMD)

Tämä opas neuvoo alusta loppuun, miten asennat ja käynnistät asiakasohjelman suoraan Windowsin **Komentokehotteesta (cmd)** ilman että sinun tarvitsee etsiä asennustiedostoja netistä.

---

## Vaihe 0: Avaa Komentokehote (CMD)
1. Paina näppäimistöltä **Windows-näppäin + R**.
2. Kirjoita avautuvaan ruutuun `cmd` ja paina **Enter**.
*(Musta komentorivi-ikkuna avautuu.)*

---

## Vaihe 1: Asenna Java ja Git suoraan Komentokehotteesta

Kopioi ja liitä seuraavat komennot Komentokehotteeseen yksi kerrallaan ja paina Enter:

### 1.1 Asenna Java (Eclipse Temurin JDK 17)
```cmd
winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements
```

### 1.2 Asenna Git
```cmd
winget install --id Git.Git -e --accept-package-agreements --accept-source-agreements
```

> ⚠️ **TÄRKEÄ VÄLIVAIHE**:  
> Kun asennukset ovat valmiit, **sulje musta Komentokehote-ikkuna ja avaa se uudelleen** (Windows + R -> `cmd` -> Enter).  
> Tämä on välttämätöntä, jotta Windows tunnistaa juuri asennetut komennot.

Voit varmistaa asennuksen ajamalla:
```cmd
java -version
git --version
```
*(Jos molemmat tulostavat versionumerot, kaikki on valmista seuraavaan vaiheeseen!)*

---

## Vaihe 2: Lataa projekti ja käännä koodi

Kopioi ja aja nämä komennot Komentokehotteessa järjestyksessä:

### 2.1 Siirry Työpöydälle ja lataa projekti GitHubista
```cmd
cd %USERPROFILE%\Desktop
git clone https://github.com/nikomiettinen/runelite-niko-scripts.git
cd runelite-niko-scripts
```

### 2.2 Käännä lähdekoodit
```cmd
gradlew.bat client:compileJava
```
*(Ensimmäisellä kerralla tässä voi mennä 1–2 minuuttia kun tarvittavat tiedostot ladataan. Lopuksi lukee `BUILD SUCCESSFUL`.)*

### 2.3 Rakenna valmis ohjelmapaketti
```cmd
gradlew.bat client:shadowJar
```

---

## Vaihe 3: Käynnistä RuneLite

Käynnistä peli komentokehotteesta suoraan komennolla:

```cmd
gradlew.bat client:run
```

*(Tai vaihtoehtoisesti valmiin jar-tiedoston kautta:)*
```cmd
java -jar runelite-client\build\libs\client-1.11.17-SNAPSHOT-shaded.jar --developer-mode
```

---

## Vaihe 4: Miten päivität koodin myöhemmin?

Jos projektiin tulee päivityksiä GitHubissa, avaa Komentokehote, siirry kansioon ja aja:
```cmd
cd %USERPROFILE%\Desktop\runelite-niko-scripts
git pull origin master
gradlew.bat client:compileJava
gradlew.bat client:shadowJar
```
