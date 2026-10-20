# Kuittikamera

Yksinkertainen Android-sovellus yrityksen kuittien kuvaamiseen ja lähettämiseen omaan sähköpostiin.

## Toiminta

1. Kirjoita oma sähköpostiosoitteesi (sovellus muistaa sen seuraavalla kerralla).
2. Paina **Ota kuva kuitista** – puhelimen kamerasovellus aukeaa.
3. Lisää halutessasi lisätieto (esim. "Lounas asiakkaan kanssa"), joka menee otsikkoon ja viestiin.
4. Paina **Lähetä sähköpostilla** – sähköpostisovellus (esim. Gmail) aukeaa valmiiksi täytettynä:
   vastaanottaja, otsikko `Kuitti 20.10.2026`, viesti ja kuva liitteenä. Paina lähetä.

Kuva pienennetään (pidempi sivu 2000 px) ja käännetään oikein päin, joten liite on yleensä alle 1 Mt
mutta kuitti pysyy luettavana.

Sovellus ei tarvitse kameran eikä internetin käyttöoikeutta: kuvaus tehdään puhelimen omalla
kamerasovelluksella ja lähetys puhelimen omalla sähköpostisovelluksella.

## APK:n hakeminen ilman Android Studiota

Jokainen push käynnistää GitHub Actionsin, joka kääntää APK:n:

1. GitHubissa: **Actions** → uusin *Build APK* -ajo → **Artifacts** → `kuittikamera-apk`.
2. Pura zip ja siirrä `app-debug.apk` puhelimeen.
3. Avaa tiedosto puhelimessa ja salli asennus tuntemattomista lähteistä.

## Kääntäminen itse

Avaa kansio Android Studiossa ja paina Run, tai komentoriviltä:

```
./gradlew assembleDebug
```

Vaatii JDK 17:n ja Android SDK:n (compileSdk 35). Tukee Android 8.0:aa ja uudempia.
