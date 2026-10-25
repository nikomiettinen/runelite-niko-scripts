SKRIPTIT SAATTAA FREEZATA (voi johtua gpu pluginista)

mlm skripti
älä käytä yläkerran deposit hopperia
lockaa hammer inventoryyn bankin asetuksista



wc plugin toimii normal,oak,yew,magic puilla

normal logs only power chop
oak varrock westissä, bank option
yew/magic wc guildissa, bank option



agility skripti toimii draynor,varrock,canifis,falador,camelot



# Vaihe 1: Asenna Java ja Git suoraan Komentokehotteesta

## 1.1 Asenna Java (Eclipse Temurin JDK 17)

```cmd
winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements
```

## 1.2 Asenna Git

```cmd
winget install --id Git.Git -e --accept-package-agreements --accept-source-agreements
```

**TÄRKEÄ VÄLIVAIHE**

**Käynnistä kone uudelleen**

**Voit varmistaa asennuksen ajamalla:**

```cmd
java -version
git --version
```

**(Jos molemmat tulostavat versionumerot, kaikki on valmista seuraavaan vaiheeseen!)**

---

# Vaihe 2: Lataa projekti githubista

```cmd
cd %USERPROFILE%\Desktop
```

```cmd
git clone https://github.com/nikomiettinen/runelite-niko-scripts.git
```

```cmd
cd runelite-niko-scripts
```

```cmd
gradlew.bat client:compileJava
```

**(Ensimmäisellä kerralla tässä voi mennä 1–2 minuuttia kun tarvittavat tiedostot ladataan. Lopuksi lukee `BUILD SUCCESSFUL`.)**

```cmd
gradlew.bat client:shadowJar
```

---

# Vaihe 3: Käynnistä RuneLite

```cmd
cd %USERPROFILE%\Desktop\runelite-niko-scripts
```

**(riippuen siitä mihin asensit)**

**Käynnistä runelite cmd kautta**

```cmd
gradlew.bat :client:shadowJar && java -ea -jar runelite-client\build\libs\client-1.13.3-SNAPSHOT-shaded.jar --developer-mode --noupdate
```

---

# Vaihe 4: Miten päivität koodin myöhemmin?

Jos projektiin tulee päivityksiä GitHubissa, avaa Komentokehote, siirry kansioon ja aja:

```cmd
cd %USERPROFILE%\Desktop\runelite-niko-scripts
```

```cmd
git pull origin master
```

```cmd
gradlew.bat client:compileJava
```

```cmd
gradlew.bat client:shadowJar
```

gradlew.bat :client:shadowJar && java -ea -jar runelite-client\build\libs\client-1.13.3-SNAPSHOT-shaded.jar --developer-mode --noupdate



## Vaihe 4: Miten päivität koodin myöhemmin?

Jos projektiin tulee päivityksiä GitHubissa, avaa Komentokehote, siirry kansioon ja aja:

cd %USERPROFILE%\Desktop\runelite-niko-scripts
git pull origin master
gradlew.bat client:compileJava
gradlew.bat client:shadowJar

