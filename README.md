# RuneLite - Asennusohje

Tämä opas neuvoo vaihe vaiheelta, mitä ohjelmia tarvitset ja miten asennat, käännät ja käynnistät asiakasohjelman omalla koneellasi.

---

## 1. Esitiedot ja vaatimukset

Ennen aloittamista varmista, että koneellesi on asennettu seuraavat kaksi työkalua:

### 1.1 Java Development Kit (JDK 17 tai JDK 21 LTS)
RuneLiten lähdekoodin kääntäminen vaatii täyden Java-kehitysympäristön (JDK). Pelkkä normaali Java (JRE) ei riitä.
- Lataa ja asenna **Eclipse Temurin JDK 17 tai 21 (LTS)**:  
  👉 [https://adoptium.net/temurin/releases/](https://adoptium.net/temurin/releases/)
- **Tärkeää asennuksen aikana**: Laita rasti ruutuihin:
  - *Set JAVA_HOME variable*
  - *Add to PATH*
- Tarkista asennuksen onnistuminen avaamalla PowerShell ja ajamalla:
  ```powershell
  java -version
  javac -version
  ```
  *(Molemmat komennot tulisi tulostaa asennettu versionumero ilman virheitä.)*

### 1.2 Git
Versionhallintatyökalu koodin lataamiseen GitHubista:
- Lataa ja asenna **Git for Windows**:  
  👉 [https://git-scm.com/download/win](https://git-scm.com/download/win)

---

## 2. Asennus ja käyttöönotto (Step-by-Step)

### Vaihe 1: Kloonaa projekti omalle koneellesi
Avaa PowerShell ja siirry kansioon, johon haluat projektin asentaa (esimerkiksi Työpöydälle):
```powershell
cd C:\Users\KÄYTTÄJÄNIMI\Desktop
git clone https://github.com/nikomiettinen/runelite-niko-scripts.git
cd runelite-niko-scripts
```

### Vaihe 2: Käännä lähdekoodit
Aja projektin päähakemistossa käännöskomento:
```powershell
.\gradlew.bat client:compileJava
```
*Ensimmäisellä ajokerralla Gradle lataa tarvittavat riippuvuudet ja kirjastot automaattisesti taustalla. Käännöksen päätteeksi tulee ilmoitus `BUILD SUCCESSFUL`.*

### Vaihe 3: Rakenna valmis paketti (ShadowJar)
Luo valmis suoritettava JAR-paketti:
```powershell
.\gradlew.bat client:shadowJar
```
Tämä kokoaa valmiin paketin hakemistoon:  
`runelite-client\build\libs\client-1.11.17-SNAPSHOT-shaded.jar`

---

## 3. Clientin käynnistäminen

Voit käynnistää peliclientin kummalla tahansa tavalla:

### Vaihtoehto A: Suoraan kehitystilassa (Gradlen kautta)
```powershell
.\gradlew.bat client:run
```

### Vaihtoehto B: Valmiin Jar-paketin kautta
```powershell
java -jar runelite-client\build\libs\client-1.11.17-SNAPSHOT-shaded.jar --developer-mode
```

---

## 4. Yleisimmät ongelmatilanteet (Troubleshooting)

### `'javac' is not recognized as an internal or external command`
- Koneeltasi puuttuu JDK tai sen polkua ei ole lisätty Windowsin PATH-ympäristömuuttujaan.
- Asenna JDK 17 tai 21 uudelleen ja varmista, että asennusvaiheessa on valittu *Set JAVA_HOME* ja *Add to PATH*. Käynnistä PowerShell uudelleen asennuksen jälkeen.

### `PSSecurityException` / `ExecutionPolicy` estää `gradlew.bat`-skriptin ajon
- Jos PowerShell estää skriptien ajamisen, anna lupa ajamalla komento:
  ```powershell
  Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
  ```

### Miten päivitän koodin uusimpaan versioon GitHubista?
```powershell
git pull origin master
.\gradlew.bat client:compileJava
.\gradlew.bat client:shadowJar
```
