# RuneLite - Automation Client (Motherlode Mine & Woodcutter)

Räätälöity RuneLite-asiakasohjelma, joka sisältää kaksi täysin automatisoitua lisäosaa Old School RuneScapeen:
1. **Motherlode Mine Script** - Täysautomaattinen louhinta, tunnelikivien poisto, hopper-syöttö ja malmien pankitus.
2. **Woodcutter Script** - Täysautomaattinen puunhakkaaja (Powerchop / maahan pudotus tai pankitus).

Molemmat lisäosat toimivat rinnakkain samassa RuneLitessä täysin toisistaan riippumattomina, ja niitä voi käyttää samalla asennuksella joko erikseen tai tilanteen mukaan.

---

## Sisällysluettelo
1. [Esitiedot ja asennusvaatimukset](#1-esitiedot-ja-asennusvaatimukset)
2. [Asennus ja käyttöönotto (Step-by-Step)](#2-asennus-ja-käyttöönotto-step-by-step)
3. [Clientin käynnistäminen](#3-clientin-käynnistäminen)
4. [Motherlode Mine Script -lisäosan käyttö](#4-motherlode-mine-script--lisäosan-käyttö)
5. [Woodcutter Script -lisäosan käyttö](#5-woodcutter-script--lisäosan-käyttö)
6. [Yleisimmät ongelmatilanteet (FAQ & Troubleshooting)](#6-yleisimmät-ongelmatilanteet-faq--troubleshooting)

---

## 1. Esitiedot ja asennusvaatimukset

Ennen aloittamista varmista, että koneellesi on asennettu seuraavat työkalut:

### 1.1 Java Development Kit (JDK 17 tai JDK 21 suositeltu, vähintään JDK 11)
RuneLiten kääntäminen ja ajaminen vaatii Java-kehitysympäristön (JDK).
- Lataa ja asenna **Eclipse Temurin JDK 17 tai 21 (LTS)**:  
  👉 [https://adoptium.net/temurin/releases/](https://adoptium.net/temurin/releases/)
- Asennuksen aikana varmista, että valitset kohdat:
  - *Set JAVA_HOME variable*
  - *Add to PATH*
- Tarkista asennus komentorivillä (PowerShell tai Command Prompt):
  ```powershell
  java -version
  javac -version
  ```

### 1.2 Git
Versionhallintatyökalu koodin lataamiseen ja päivittämiseen.
- Lataa ja asenna Git Windowsille:  
  👉 [https://git-scm.com/download/win](https://git-scm.com/download/win)

---

## 2. Asennus ja käyttöönotto (Step-by-Step)

### Vaihe 1: Kloonaa repositorio omalle koneellesi
Avaa PowerShell ja siirry haluamaasi kansioon (esimerkiksi Työpöydälle):
```powershell
cd C:\Users\KÄYTTÄJÄNIMI\Desktop
git clone https://github.com/nikomiettinen/runelite-mlm-skripti.git
cd runelite-mlm-skripti
```

### Vaihe 2: Käännä projekti
Käännä Java-lähdekoodit ajamalla:
```powershell
.\gradlew.bat client:compileJava
```
Ensimmäisellä kerralla Gradle lataa tarvittavat riippuvuudet automaattisesti. Käännöksen tulisi päättyä tekstiin `BUILD SUCCESSFUL`.

### Vaihe 3: Rakenna valmis paketti (ShadowJar)
Luo valmis suoritettava JAR-paketti:
```powershell
.\gradlew.bat client:shadowJar
```
Tämä luo täysin pakatun asiakasohjelman hakemistoon:  
`runelite-client\build\libs\client-<versio>-shaded.jar`

---

## 3. Clientin käynnistäminen

Voit käynnistää peliclientin suoraan komentoriviltä kahdella tavalla:

### Tapa A: Gradlen kautta suoraan (Kehitystila)
```powershell
.\gradlew.bat client:run
```

### Tapa B: Valmiin Jar-tiedoston kautta
```powershell
java -jar runelite-client\build\libs\client-1.11.17-SNAPSHOT-shaded.jar --developer-mode
```

---

## 4. Motherlode Mine Script -lisäosan käyttö

1. Kirjaudu sisään hahmollasi ja siirry Motherlode Mineen.
2. Avaa RuneLiten oikean reunan jakoavain-ikoni (**Configuration / Asetukset**).
3. Etsi hakusanalla: `Motherlode Mine Script`.
4. Ota lisäosa käyttöön kytkimestä.
5. Avaa lisäosan asetukset (ratas-ikoni):
   - **Käynnistä Motherlode Mine** -> Laita päälle (`ON`), kun haluat skriptin aloittavan louhinnan.
   - **Minimipanos reppuun** -> Oletus `26` (tai `25` jos vasara tai gem bag on repussa).
   - **Pura esteet (Rockfalls)** -> Pidä päällä (`ON`).
6. Ruudun vasempaan yläkulmaan ilmestyy tilapaneeli, joka kertoo reaaliajassa hahmon tilan, repun sisällön ja säädetyn louhintatason.

---

## 5. Woodcutter Script -lisäosan käyttö

1. Kirjaudu sisään hahmollasi ja mene minkä tahansa puiden lähelle (kirves kädessä tai repussa).
2. Avaa RuneLiten oikean reunan jakoavain-ikoni (**Configuration / Asetukset**).
3. Etsi hakusanalla: `Woodcutter Script`.
4. Ota lisäosa käyttöön kytkimestä.
5. Avaa lisäosan asetukset:
   - **Käynnistä Woodcutter** -> Laita päälle (`ON`).
   - **Puutyyppi** -> Valitse haluamasi puu (esim. `Normal Tree`, `Oak`, `Willow`, `Maple`, `Yew`, `Magic`, `Redwood` tai `Kaikki puut`).
   - **Toimintatila** ->
     - `Drop logs (Powerchop)`: pudottaa halot maahan repun täyttyessä (kirvestä, linnunpesiä tai cluita ei koskaan pudoteta).
     - `Bank logs`: kävelee lähimmälle pankkitiskille/arkulle/pankkiirille ja tallettaa halot.
   - **Maksimietäisyys**: Määritä kuinka monen laatan päästä hahmo hakee seuraavaa puuta (oletus: 15).
6. Ruudun vasempaan yläkulmaan tulee paneeli, joka näyttää hakattujen halkojen määrän, tilan ja istunnon keston.

---

## 6. Yleisimmät ongelmatilanteet (FAQ & Troubleshooting)

### Kysymys: Kääntö epäonnistuu virheeseen `'javac' is not recognized` tai Java-virheeseen
- **Ratkaisu**: Varmista, että koneellesi on asennettu JDK 17 tai JDK 21 (pelkkä JRE ei riitä). Tarkista ympäristömuuttuja `JAVA_HOME` ja että sen arvo osoittaa JDK-asennushakemistoosi (esim. `C:\Program Files\Eclipse Adoptium\jdk-17...`).

### Kysymys: Gradle valittaa käyttöoikeuksista (`Permission denied`)
- **Ratkaisu**: Avaa PowerShell järjestelmänvalvojana (Run as Administrator) ja aja komento:
  ```powershell
  Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
  ```

### Kysymys: Miten päivitän koodin, jos teen siihen muutoksia?
- **Ratkaisu**: Koodin muokkaamisen jälkeen aja uudestaan:
  ```powershell
  .\gradlew.bat client:compileJava
  .\gradlew.bat client:shadowJar
  ```
  Ja commit & push GitHubiin:
  ```powershell
  git add .
  git commit -m "Oma viestisi"
  git push origin master
  ```

---
