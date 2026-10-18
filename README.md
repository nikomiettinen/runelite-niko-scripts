# RuneLite - Motherlode Mine Automation Client

Räätälöity RuneLite-asiakasohjelma, joka sisältää täysin automatisoidun **Motherlode Mine Script** -lisäosan Old School RuneScapeen.

---

## Sisällysluettelo
1. [Esitiedot ja asennusvaatimukset](#1-esitiedot-ja-asennusvaatimukset)
2. [Asennus ja käyttöönotto (Step-by-Step)](#2-asennus-ja-käyttöönotto-step-by-step)
3. [Clientin käynnistäminen](#3-clientin-käynnistäminen)
4. [Motherlode Mine Script -lisäosan käyttö pelissä](#4-motherlode-mine-script--lisäosan-käyttö-pelissä)
5. [Toimintalogiikka ja ominaisuudet](#5-toimintalogiikka-ja-ominaisuudet)
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
git clone https://github.com/nikomiettinen/runelite.git
cd runelite
```

*(Koska repositorio on yksityinen, Git pyytää kirjautumaan GitHub-tunnuksellasi tai käyttämään Personal Access Tokenia.)*

---

## 3. Clientin käynnistäminen

Voit käynnistää clientin kahdella eri tavalla:

### Vaihtoehto A: Suora käynnistys Gradlella (Suositeltu kehitykseen ja testaukseen)
Projektin mukana tulee Gradle Wrapper, joten sinun ei tarvitse asentaa Gradlea erikseen koneelle:

```powershell
.\gradlew.bat client:run
```
*(Ensimmäisellä ajokerralla Gradle lataa tarvittavat riippuvuudet, minkä jälkeen RuneLite-asiakasohjelma avautuu automaattisesti kehittäjätilassa `--developer-mode`.)*

### Vaihtoehto B: Valmiin JAR-paketin kääntäminen ja ajaminen
Jos haluat paketoida clientin itsenäiseksi JAR-tiedostoksi:
```powershell
# 1. Käännä valmis paketti
.\gradlew.bat client:shadowJar

# 2. Käynnistä käännetty JAR-paketti
java -jar runelite-client\build\libs\client-1.13.3-SNAPSHOT-shaded.jar --developer-mode
```

---

## 4. Motherlode Mine Script -lisäosan käyttö pelissä

1. Kun RuneLite avautuu, kirjaudu sisään pelitilillesi Old School RuneScapeen.
2. Matkusta hahmollasi **Motherlode Mineen** (Dwarven Mines / Falador).
3. Avaa RuneLiten oikeasta sivupalkista jakoavain-ikoni (**Configuration / Plugin list**).
4. Kirjoita hakukenttään: `Motherlode Mine Script`.
5. Kytke lisäosa päälle liukukytkimestä.
6. Avaa pluginin asetukset ratasikonista:
   - **Start Script**: Käynnistää / pysäyttää automaattisen rutiinin.
   - **Min Pay-dirt to Deposit**: Reppuraja talletukseen (oletus: `26`).
   - **Bigger Sack Unlocked**: Kytke päälle vain, jos olet ostanut Prospector Percy'lta isomman säkin (tallettaa 7 reppua 4 sijaan ennen tyhjennystä).
   - **Clear Rockfalls**: Hakkaa reitillä olevat tunnelikivet automaattisesti pois (oletus: `Päällä`).
   - **Repair Struts**: Korjaa deposit hopperin vieressä olevan rikkinäisen vesirattaan tuen (oletus: `Päällä`).
   - **Drop Gems**: Pudottaa louhinnasta saadut raa'at jalokivet automaattisesti maahan repun säästämiseksi (oletus: `Päällä`).
   - **Mine Upstairs**: Valitse tämä, jos haluat botin kiipeävän tikkaita yläkerran tasanteelle louhimaan (oletus: `Pois`).

Ruudun vasempaan yläkulmaan ilmestyy **Motherlode Mine Overlay**, josta näet reaaliajassa:
- Nykyisen tilan (`Current State`)
- Reppukohtaisen pay-dirtin määrän (`Pay-dirt: X / 26`)
- Talletettujen reppujen laskurin (`Deposited Invs: X / 4`)
- Säkin malmimäärän (`Sack Ore Count: X / 108`)

---

## 5. Toimintalogiikka ja ominaisuudet

Lisäosa toteuttaa täydellisen syklin ilman manuaalista puuttumista:

1. **Malmisuonten louhinta**:
   - Hakkaa luoteisen alakerran malmisuonia (`3728, 5688`).
   - Hakkaa kunkin suonen loppuun asti (odottaa suonen ehtymistä) ennen seuraavan klikkausta, välttäen turhia klikkauksia.
   - Louhinnan aikana (repun ollessa 1–25) rockfalleja ei hakata turhaan.
2. **Kulkureitin turvaus (Rockfall-logiikka)**:
   - Tunnistaa tarkasti 3 kriittistä tunnelikiveä:
     - Itäinen kivi (hopperin pääty): `(3733, 5680)`
     - Keskimmäinen kivi: `(3731, 5683)`
     - Läntinen kivi (luolan suu): `(3727, 5683)`
   - Kun reppu on täynnä (`26/26`) tai tyhjä (`0/26`), hahmo hakkaa reitillä olevat kivet oikeassa järjestyksessä pois eikä koskaan harhaudu muualle kaivokseen.
3. **Talletus hopperiin**:
   - Vie pay-dirtit alakerran hopperiin (`3748, 5672`).
   - Mikäli viereinen vesirattaan tuki (`3742, 5669`) on rikki, hakee tarvittaessa laatikosta vasaran ja korjaa vain tämän kyseisen lähimmän tuen.
4. **Säkin tyhjennys & pankitus**:
   - 4 täyden repun jälkeen (tai kirjautuessa sisään säkin sisältäessä malmeja) skripti kävelee säkille (`3748, 5659`), tyhjentää malmit, juoksee pankkiarkulle (`3759, 5664`) ja tallettaa malmit yhdellä klikkauksella.
   - Säkin tyhjennyksen jälkeen palaa suoraan takaisin tunnelikivien kautta louhintahuoneeseen.

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

## Lisenssi
Tämä projekti pohjautuu avoimen lähdekoodin RuneLite-asiakasohjelmaan (BSD 2-Clause License).
