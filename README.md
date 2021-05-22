# kotlinx-serialisation-kvon

KVON (or **K**otlin **V**ersioned **O**bject **N**otation) is a made up serialisation format that's very close to JSON, but has implicit redundancy compression for collections of objects.

## Huh?

Take the following simple example:

```json
[
    {
        "identity": "test",
        "isTest": true
    },
    {
        "identity": "test",
        "isTest": false
    },
    {
        "identity": "test",
        "isTest": false
    },
    {
        "identity": "test",
        "question": "or is it?"
    }
]
```

There's a fair amount of duplication here; the 'identity' field is duplicated here across all four objects. However, encoding with KVON -

```json
[
    {
        "identity": "test",
        "isTest": true
    },
    {
        "isTest": false
    },
    {
    },
    {
        "question": "or is it?",
        "isTest": ">\u0018<"
    }
]
```

KVON strips out the duplicated fields, bumping the file size down to ~70% of what it was.

When a field is *removed* from future objects, a special string is encoded to indicate that the property has been removed.

## But wait, what about compression / msgpack?

Who's to say that compression doesn't *stack* with KVON? KVON doesn't perform compression on strings or the like, so there's still room to improve with compression.

Now in our simple example above, gz compression actually achieves a *smaller* file than KVON, even when using KVON + gz, however on larger files the gap starts to widen.

Using some test files from [Blaseball](https://blaseball.com) -

```
=====[ Miami Dale vs. Ohio Worms ]=====
Normal JSON (ca251a6a2bc4e227b1a9984ca554695d)
    - 1.59 MB raw [100.0%]
    - 31.59 kB gz  [ 1.99%]

KVON (84d7c76694caf2c763c9e8b83aad17b8)
    - 99.88 kB raw [ 6.29%]
    - 9.46 kB gz  [  0.6%]

Normal JSON w/ MsgPack (d39e09905ffc1d3c4ddb9dc96ba3731d)
    - 1.42 MB raw [89.25%]
    - 31.66 kB gz  [  2.0%]

KVON w/ MsgPack (aed2ab395f899f3f47aa89e53babcad2)
    - 92.13 kB raw [ 5.81%]
    - 9.9 kB gz  [ 0.62%]
```

While using gz compression drops this file down to ~31 kB normally, coupling KVON with gz gets it down to only *9 kB*, which is .6% of the original file size.

MsgPack gives smaller boosts here, but may still be worthwhile - any method of reducing the size of JSON should be fully compatible, since KVON is just a trimmer.

## Woah, that's insane! How do I use it?

KVON provides a few different ways of utilising it:

- `JsonElement#compressElementWithKvon` and `JsonElement#decompressElementWithKvon` are manual methods that allow you to pass a previous value in, accommodating streaming data from a file or socket.
- `Json#encodeToKvonString` and `Json#decodeFromKvonString`, `Json#encodeToKvonElement` and `Json#decodeFromKvonElement` allow you to encode an object into KVON, or decode KVON into an object.
- Any place you would pass a serialiser, you can pass `KvonSerialiser`

- ![kotlinx-serialisation-kvon](https://img.shields.io/maven-metadata/v?label=kotlinx-serialisation-kvon&metadataUrl=https%3A%2F%2Fmaven.brella.dev%2Fdev%2Fbrella%2Fkotlinx-serialisation-kvon%2Fmaven-metadata.xml)

Gradle

```groovy
repositories {
  maven { url "https://maven.brella.dev" }
}

dependencies {
  implementation "dev.brella:kotlinx-serialisation-kvon:1.0.0"
}
```
