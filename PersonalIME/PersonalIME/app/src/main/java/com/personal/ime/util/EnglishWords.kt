package com.personal.ime.util

/**
 * 内置英文词表（约 10000 词），按常用度大致排序（越靠前越常用）：
 * 高频功能词 → 日常用语 → 计算机专业词汇 → 六级/托福词汇 → 分类词汇 → 屈折形式。
 * 预测时词表索引作为基础得分，数据库中的科技词汇与用户学习记录会获得更高权重。
 */
object EnglishWords {

    val WORDS: List<String> = """
        the be to of and a in that have it for not on with he as you do at
        this but his by from they we say her she or an will my one all would
        there their what so up out if about who get which go me when make can
        like time no just him know take people into year your good some could
        them see other than then now look only come its over think also back
        after use two how our work first well way even new want because any
        these give day most us is are was were been being am has had did does
        done made makes go went gone comes came coming gets got getting took
        taken saw seen knew known thought find found gave given tell told ask
        asked seemed feel felt tried leave left call called keep kept let
        begin began help start started showed hear heard play played run move
        lived believe hold held brought happened write wrote written provide
        stand stood lose lost paid meet met learn lead understand watch follow
        stop create speak spent grew open walked win won teach taught offer
        remember consider appear bought serve sent build built stay fall fell
        mean meant put cut
        hello hi hey thanks thank please sorry welcome ok okay yes yeah
        morning afternoon evening night today tomorrow yesterday week month
        hour minute second man woman men women child children family friend
        friends father mother brother sister son daughter wife husband baby
        home house room door window table chair bed kitchen bathroom school
        class teacher student book books pen paper word words letter name
        number phone mobile computer laptop screen keyboard mouse file files
        folder page site website app data code server client user users
        password account email message messages photo photos picture pictures
        video videos music song songs movie movies game games food drink
        water coffee tea breakfast lunch dinner fruit apple rice bread meat
        fish milk egg eggs car bus train plane bike road street city country
        world place places thing things part kind sort type example question
        questions answer answers problem problems idea ideas plan plans
        reason fact case point line end side area money price cost market
        business company companies job jobs office team project projects
        task tasks report meeting meetings customer customers service
        product products order orders shop store sale news information story
        history life love heart mind head eye eyes face hand hands body
        health rest sleep dream hope happy sad angry busy free ready late
        early fast slow big small large little long short high low old young
        new hot cold warm cool dry wet clean dirty easy hard difficult simple
        complex important possible impossible different same similar best
        better worse worst great nice beautiful pretty cute funny interesting
        boring true false right wrong safe dangerous strong weak light dark
        quiet loud rich poor cheap expensive full empty open closed public
        private general common special normal strange local national
        international official popular famous modern ancient future past
        present recent able available necessary useful useless serious fun
        real whole half many much more less few several both each every some
        another such
        become becomes became remain remains continue continues change changes
        changed develop developed developing include includes including
        receive received agree agreed allow allowed arrive arrives arrived
        carry cause check choose clean collect complete contain cross decide
        deliver depend design destroy discover discuss drive drop enjoy enter
        expect experience explain explore express extend finish forget forgive
        gain gather hide hit hurt imagine improve increase introduce invite
        join jump kick land laugh lend lift listen maintain manage mark marry
        match matter measure mention miss mix need obtain paint pass pick
        point pour practice prefer prepare prevent produce promise protect
        prove raise reach realize recognize reduce refuse regret remove repair
        repeat reply require result return review reward ride ring rise risk
        roll rule search sell set shake shine shoot shout show sing sit slide
        smell smile solve sort spell stare steal step stick stretch strike
        study succeed suggest support suppose surprise sweep swim talk taste
        thank throw touch travel trust visit vote wait wake walk wash wave wear
        whisper wish wonder worry wrap write yawn
        very really quite rather almost always never often sometimes usually
        rarely suddenly finally quickly slowly carefully easily loudly quietly
        together alone again once twice soon later already yet still just only
        even also probably possibly certainly definitely perhaps maybe indeed
        however therefore otherwise besides moreover furthermore instead
        meanwhile unfortunately fortunately
        although though unless until since while whether before during through
        between among across behind below above under near beside against
        without within along around beyond despite except inside outside
        monday tuesday wednesday thursday friday saturday sunday january
        february march april june july august september october november
        december weekend weekday holiday vacation midnight noon dawn dusk
        zero three four five six seven eight nine ten eleven twelve thirteen
        fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty
        forty fifty sixty seventy eighty ninety hundred thousand million
        billion couple dozen pair double single triple
        water fire earth air wood metal glass plastic cloth leather silver
        gold iron steel stone sand dust smoke cloud storm thunder lightning
        rainbow shadow silence noise weather rain snow wind sun sky star
        tree flower grass river mountain sea beach park garden forest field
        animal dog cat bird horse cow sheep pig chicken duck rabbit mouse
        snake fish bear wolf lion tiger elephant monkey panda deer
        color red blue green yellow black white gray grey brown pink purple
        orange silver golden
        love hate fear joy happiness sadness anger surprise trust jealous
        proud ashamed embarrassed excited nervous relaxed calm worried anxious
        confident shy brave careful clever foolish gentle lazy lively loyal
        modest polite rude sensible silly stubborn wise
        doctor nurse dentist lawyer farmer chef driver pilot police officer
        soldier scientist artist musician singer dancer actor painter designer
        programmer developer manager director president secretary clerk
        salesman cashier waiter waitress journalist photographer fisherman
        hunter tailor barber mechanic electrician plumber carpenter builder
        miner sailor engineer architect accountant translator guide guard
        assistant intern expert consultant coach judge mayor minister
        football basketball soccer tennis baseball volleyball golf hockey
        swimming running boxing skiing surfing cycling skating baseball
        ball goal score match game race team player coach referee stadium
        ball bat racket net field court track pool gym
        shirt pants trousers jeans dress coat jacket hat cap shoes boots
        socks gloves scarf belt wallet watch ring necklace bracelet
        glasses umbrella bag backpack suitcase box basket bottle cup glass
        plate bowl spoon fork knife chopsticks
        airport station hotel restaurant supermarket hospital bank library
        museum theater cinema zoo park church bridge tower castle
        ticket passport visa luggage map guide hotel room key lock alarm
        chat group post comment follow share blog forum blog wiki
        internet network online offline download upload update updates
        install version release feature features bug bugs test tests testing
        debug deploy database backup device system settings notification
        battery charge cable wifi bluetooth login logout register profile
        link copy paste save delete search filter select choose edit view
        list grid menu button icon label title header footer content
        language english chinese japanese grammar sentence accent voice sound
        level amount total count average percent quarter
        algorithm algorithms data structure structures variable variables
        constant constants function functions method methods class classes
        object objects interface interfaces inheritance polymorphism
        encapsulation abstraction constructor parameter parameters argument
        arguments return value null void boolean integer float double string
        char byte short long array matrix vector list queue stack heap tree
        graph hash table dictionary map set tuple record pair entry node
        root leaf branch edge vertex path depth height level order traversal
        search sort sorted sorting merge quick heap bubble insertion selection
        binary linear recursive recursion iteration iterative loop while
        condition switch break continue statement syntax semantics parser
        compiler interpreter runtime compile bytecode garbage collector
        memory pointer reference dereference scope namespace import export
        package module library framework dependency dependencies annotation
        comment documentation readme license
        code coding coder programmer developer programming software hardware
        source target build rebuild clean sync gradle maven makefile script
        shell bash powershell terminal console command prompt execute
        process thread mutex semaphore deadlock race condition scheduler
        kernel driver interrupt signal syscall
        test testing unit integration coverage mock stub spy fixture assert
        expectation coverage report regression
        debug debugger breakpoint watch inspect trace profile profiler
        performance benchmark optimization optimize refactor refactoring
        legacy clean readable maintainable scalable robust
        version control revision history diff conflict resolve merge branch
        commit push pull clone fetch rebase stash tag cherry release
        repository remote fork issue ticket milestone label
        database table column row field index primary foreign key constraint
        schema query insert update delete select join union group aggregate
        limit offset transaction isolation lock trigger view procedure
        normalization migration backup restore replica cluster node master
        slave consistency sharding partition
        sql nosql mysql postgresql mongodb redis sqlite oracle
        server client host domain url uri request response header body
        cookie session token api endpoint rest graphql rpc websocket
        http https ftp smtp imap pop dns dhcp tcp udp port socket
        firewall router switch gateway proxy cache bandwidth latency
        throughput packet frame segment protocol handshake timeout retry
        redirect load balancer
        html css javascript typescript jsx tsx frontend backend fullstack
        browser render layout style sheet script ajax fetch promise async
        await component state props hooks router middleware
        react vue angular nodejs express nextjs sass less webpack babel
        eslint prettier responsive
        security vulnerability exploit malware virus worm trojan ransomware
        phishing spam scam hacker attack defense encryption decryption
        authentication authorization permission role access jwt oauth
        csrf xss injection brute force sniffing spoofing social engineering
        penetration testing patch scanner payload privilege escalation
        botnet ddos
        cloud computing virtualization container docker kubernetes
        orchestration microservice serverless scaling autoscaling monitoring
        alerting logging tracing metrics dashboard grafana prometheus
        terraform ansible jenkins gitlab bamboo argo helm chart registry
        image repository pipeline workflow automation infrastructure
        provisioning configuration deployment bluegreen canary rolling
        rollback healthcheck readiness liveness probe
        machine learning deep neural network neuron layer weight bias
        activation gradient descent backpropagation overfitting
        underfitting regularization dropout batch normalization convolution
        pooling embedding transformer attention encoder decoder sequence
        token vocabulary generative model training validation epoch
        iteration loss accuracy precision recall confusion matrix feature
        engineering selection extraction dimensionality reduction clustering
        classification regression reinforcement supervised unsupervised
        dataset corpus inference fine tuning prompt tensor cuda
        pytorch tensorflow keras numpy pandas matplotlib opencv
        processor cpu gpu memory ram rom cache register bus clock
        motherboard chipset firmware bios driver peripheral usb display
        monitor printer scanner speaker microphone camera sensor
        storage disk ssd hdd sector cluster filesystem mount partition
        format fragmentation power supply cooling fan heatsink
        ascii unicode encoding utf character bit kilobyte megabyte gigabyte
        terabyte petabyte binary decimal hexadecimal octal bitmask bitwise
        operator shift endian checksum digest md5 sha compression archive
        zip tar gzip complexity logarithmic quadratic exponential
        android ios activity fragment service intent broadcast receiver
        content provider lifecycle viewbinding recyclerview adapter layout
        kotlin swift flutter dart native widget permission optimization
        linux windows macos ubuntu debian centos fedora arch unix
        emacs vim nano sed awk grep curl wget ssh scp tar
        python ruby rails django flask spring boot hibernate rust golang
        java swift kotlin scala perl lua matlab r julia
        github gitlab bitbucket stackoverflow dockerhub npm pypi maven
        vscode intellij eclipse xcode androidstudio sublime
        nginx apache tomcat graphql grpc websocket jwt ssl tls cdn
        oauth websocket lambda edge region zone instance bucket
        agile scrum sprint backlog retrospective kanban story points
        estimation priority roadmap release candidate stakeholder
        laptop desktop notebook tablet keyboard mouse touchpad screen
        charge cable adapter port plug switch router modem
        absolutely totally exactly basically literally actually currently
        previously eventually immediately directly nearly hardly barely either
        neither none nobody nothing something anything everything someone
        anyone everyone somebody anybody everybody somewhere anywhere
        everywhere
        achieve achieved acquire acquire adapted adjust admit admire adopt
        advise afford aim analyze announce annoy apologize appeal apply argue
        arrange arrest attach attempt attend avoid bake behave belong bite
        blame bless boil breathe burn calculate cancel capture celebrate
        challenge chase climb comb complain concentrate confess confuse
        congratulate connect consist convince correct crawl criticize curve
        damage dare decorate delay deserve detect disagree disturb divide drag
        drown earn educate embarrass encourage entertain escape examine excite
        exercise exist expand explode fold frighten fry glue grab greet hang
        hate heal heat hug hunt ignore inform injure instruct invent irritate
        jog knock launch lick memorize migrate multiply nod obey occur offend
        owe park perform pinch polish pop praise pray predict preserve print
        pronounce punch purchase puzzle quote race recognize record reflect
        regard relax release relieve rent repay replace reproduce rescue retire
        retrieve roast rub rush satisfy scare scatter scream seal seize shave
        shock sigh sign ski slam slap slip smash smoke sneeze sniff sob spill
        spin squeeze stab stain steer stitch stir strip survive suspect sweat
        swing tap tear tease tend threaten thrive tick tighten tip toast
        tolerate toss trace trade train transfer transport trap treat tremble
        twist underline undo unfold unload unlock untie urge vanish ventilate
        verify vibrate volunteer wander warn waste whistle wink wipe withdraw
        yell zip
        ability absence accident achievement advantage adventure advice affair
        agreement attention attitude awareness balance behavior belief benefit
        birth bottom breath career ceremony chance character charity choice
        comfort communication competition complaint concern confusion
        connection consequence consideration context contribution control
        conversation courage criticism culture curiosity danger decision defeat
        denial description desire detail determination difference difficulty
        disappointment discipline discussion distance distribution duty economy
        education effect effort emotion emphasis energy environment error event
        evidence evolution examination excitement existence expansion
        expectation explanation expression failure faith fault favor feeling
        fiction flavor flexibility fortune freedom friendship generation glory
        goal growth guidance habit harm hatred horizon identification
        imagination impact impression improvement independence indication
        industry influence insight instance instruction insurance intelligence
        intention interaction introduction invention investment judgment justice
        knowledge lack laughter leadership length limit luck luxury magnitude
        manner meaning memory mistake mood motion motivation movement mystery
        nature necessity negotiation observation opportunity opposition option
        organization outcome output peace perception permission personality
        perspective phenomenon philosophy pleasure policy population position
        possibility poverty practice pressure pride principle privilege
        probability process production progress promise promotion proof
        proportion proposal protection provision psychology publicity purpose
        quality quantity reaction reality recognition recommendation recovery
        reflection region regret regulation relation relief religion remark
        reputation requirement research reservation resistance resolution respect
        responsibility restriction retirement reward role routine safety sample
        science selection sense separation series shame skill society solution
        sorrow source species statement statistics status stress structure
        success suffering suggestion summary surprise sympathy talent temper
        tendency theory thought threat title tolerance tradition tragedy
        training transformation trouble truth understanding union unity utility
        variety version violence virtue wealth welfare wisdom worth
        abrupt absurd accurate active actual additional adequate adult advanced
        aggressive alive amazing annual apparent appropriate artificial
        attractive automatic awful awkward balanced beneficial bold bored
        brilliant broad capable casual civil classic cloudy coarse comfortable
        commercial complete confident conscious convenient correct curious daily
        damp deadly definite delicate democratic dependent desperate digital
        distant doubtful dramatic dull eager earnest economical educational
        effective efficient elaborate energetic enormous essential exact
        excellent experienced fair fancy fantastic fashionable fatal feasible
        fertile fierce formal frank frequent frightened fundamental generous
        giant glad graceful gradual grand grateful greedy gross grown handsome
        handy hollow honest hopeless horrible huge hungry ideal immense
        impatient impressive inadequate incomplete increasing indefinite innocent
        instant intelligent intense internal jealous joint lively magnificent
        massive mature medium mere mild minor miserable moist moral narrow
        natural naughty nearby neat negative noticeable obvious odd optional
        ordinary outstanding overcast painful partial particular passive
        patient permanent personal plain pleasant positive powerful precious
        previous primary prime probable professional proper rapid rare rational
        realistic reasonable regular relative reliable remarkable remote
        repeated representative responsible ripe rude rural satisfactory scarce
        scientific secondary secret secure shady significant silent sincere
        skilled sleepy slight smart smooth soft solid sophisticated sore
        spacious spectacular speedy steady steep sticky stiff stubborn stunning
        stupid sufficient suitable sunny superb superior surprised suspicious
        sweet tasty temporary tender tense terrible thick thin thirsty thorough
        tiny tough transparent typical uncertain uncomfortable unconscious
        unfair unfortunate unique universal unpleasant unusual upset urban urgent
        vague vast wicked wide wild wonderful
        ram rom cpu gpu api sdk ide os ui ux qa vm db saas paas iaas
        idempotent stateless circuit breaker fallback throttle debounce
        serialize deserialize marshal unmarshal encode decode lexer tokenizer
        grammar decompile obfuscate minify uglify transpile polyfill hotfix
        patch minor major beta alpha stable nightly snapshot artifact metadata
        seed checkpoint invalidate
        subnet mask cidr vlan vpn tunnel iptables topology mesh star ring bus
        lan wan pan ethernet hotspot access point roaming antenna fiber optic
        twisted pair coaxial mac address arp icmp rip ospf bgp multicast
        broadcast anycast unicast
        hyperlink iframe dom render engine gecko blink webkit chromium firefox
        safari opera edge chrome devtools inspector waterfall storage indexeddb
        serviceworker pwa cors csp sri
        hash salt pepper hmac aes rsa des ecc bcrypt scrypt argon pbkdf
        certificate x509 pki csr crt pem der keystore truststore jks hsts
        referrer samesite httponly
        lstm gru rnn cnn gan vae diffusion tensorboard augment augmentation
        transform normalize standardize stochastic momentum adam sgd rmsprop
        nadam early stopping
        acid cap base crud orm jdbc odbc pool connection prepared execute
        batch fetch scroll dirty read phantom repeatable serializable mvcc wal
        journal page block extent segment tablespace
        grep find xargs chmod chown ps top kill nice cron daemon fifo mmap
        malloc calloc realloc free brk page fault swap thrash
        swiftui compose jetpack coroutines flow livedata viewmodel room
        retrofit okhttp gson moshi coil glide dagger hilt koin
        jvm jre jdk classpath jar war ear reflection generics lambda stream
        optional sealed vararg suspend inline reified extension companion
        blame bisect reflog submodule lfs hook precommit
        epic spike velocity burndown persona wireframe mockup prototype
        usability funnel retention churn
        resume interview candidate recruiter salary bonus promotion teammate
        colleague mentor jargon buzzword
        ls cd pwd cp mv rm mkdir rmdir touch cat less more head tail locate
        which whereis whoami who hostname uname uptime free df du htop pkill
        killall jobs bg fg nohup screen tmux sudo su passwd chgrp umask ln
        gunzip bzip2 xz rsync sftp ssh curl wget ping traceroute tracepath
        netstat ifconfig nmcli systemctl service journalctl dmesg lspci
        lsusb lscpu lsblk fdisk parted umount fstab crontab alias export env
        printenv source bash zsh fish apt apt yum dnf pacman zypper snap
        flatpak dpkg rpm make clang gdb strace lsof nmap ufw firewalld
        man info history clear exit logout reboot shutdown poweroff
        timedatectl hostnamectl loginctl vim nano emacs tee xargs sort uniq
        wc cut tr column jq diff patch basename dirname realpath readlink
        stat file chattr lsattr chroot visudo useradd usermod userdel
        groupadd adduser swapon swapoff mkfs fsck blkid sync udevadm
        modprobe insmod rmmod lsmod sysctl insmod mkswap swapon
        distribution distro debian ubuntu fedora arch manjaro opensuse
        gentoo centos rocky alma kali mint elementary zorin popos
        package dependency repository ppa aur deb snapcraft appimage
        portable desktop gnome kde plasma xfce lxde lxqt cinnamon mate i3
        sway wayland xorg x11 compositor shell terminal tty pty console
        bootloader grub initramfs root filesystem ext4 btrfs xfs zfs
        partition mountpoint proc sysfs udev systemd unit socket timer
        target journal cgroup namespace sandbox selinux apparmor symlink
        hardlink superblock journaling anacron bashrc zshrc vimrc
        known_hosts authorized_keys passphrase agent forwarding tunneling
        dbus udev rule kernel module parameter cmdline runlevel
        rescue mode prompt glob wildcard redirection stdin
        stdout stderr profile aliasing completion plugin theme icon font
        mechanic mechanical membrane keycap switch cherry brown blue
        numpad touchpad trackball trackpoint stylus digitizer tablet
        lcd led oled ips panel resolution refresh rate aspect brightness
        contrast gamut hdr freesync gsync bezel stand arm vesa
        subwoofer headset earphone webcam laser inkjet copier fax plotter
        projector whiteboard dock docking station card reader microsd
        flashdrive pendrive nas raid joystick gamepad controller steering
        wheel pedal vr barcode fingerprint reader smartcard ups surge
        protector strip charger typec thunderbolt displayport vga dvi
        toslink coaxial spdif infrared nfc ble sim
        motherboard chipset socket cooler heatsink aio thermal paste
        compound chassis psu wattage modular cabling dimm sodimm ddr
        ddr ecc timing slot channel vram graphics sound codec module
        modem platter spindle tbw hbm
        typea typeb typec micro mini lightning ps rs sas riser
        overclock underclock undervolt boost tdp throttle bottleneck
        compatibility form atx itx matx eatx sff san das enclosure bay
        backplate bracket screw standoff curve airflow static pressure
        decibel rgb aura cmos jumper xmp expo voltage rail efficiency
        rating bronze gold platinum titanium
        everyday nouns expansion
        age animal art ball band bath beach bear beauty bell belt bench
        birthday bit block blood blow board boat body bone box boy brain
        branch bridge bubble budget building burn bush button camp candle
        cap captain card care career case cat cave ceiling cell cent chance
        charge chat check cheek cheese chest chicken chief chin chocolate
        choice church circle coat coin cold collar collection college color
        comb company condition concert contact contest control cook corner
        cost cotton count couple course cousin cover cow crash cream credit
        crime crowd crown culture cup curve custom damage dance danger deal
        death debt decision deer degree delivery demand department depth
        desert design desire desk detail development difference difficulty
        direction dirt disaster discount discussion disease distance
        district division doctor dog dollar donation doubt draft drama dream
        dress driver drop drum duty east economy edge education effect
        effort elevator emergency emotion employee employer end energy
        engine entertainment entrance environment equipment error event
        examination exchange excitement exercise exhibition exit experience
        experiment expert explanation expression eye face fact factory fan
        farm farmer fashion fat favor fear feather feature fee feed feeling
        fence fever field fight figure finger fire firm flag flame flavor
        flight floor flow flower fog foot football forest fork form fortune
        foundation fountain fox frame freedom fuel fun funeral furniture
        future garden gate gift girl glass glove goal goat god grade grain
        grass gravity gray ground group growth guard guest guide guitar gun
        habit hair half hall hand handle happiness harm hat hate head health
        heart heat heel height hell hero hill hip history hole holiday honey
        hope horn hospital hotel ice identity image imagination impact
        import importance impression improvement incident income increase
        industry influence information ingredient injury insect inside
        inspection instance instrument insurance intention interest internet
        interview introduction invention investment invitation iron island
        item jacket jam jar jaw jeans jelly jewel job joint joke journey joy
        judge juice jump key kid king knee knife knot lab lady lake lamp land
        language laugh law lawyer layer leader league leg length lesson
        level library lie life lift light limit line lion lip list
        literature lock log logic love luck machine magazine mail manager
        manner map mark market marriage master match material matter meal
        meaning measure meat medicine medium member memory mention menu
        metal method middle mile mind mine minister mirror miss mission
        mistake mix model moment money monitor mood moon mountain mouth
        movement mud muscle museum nail nation nature neck need neighbor
        nerve nest net news newspaper noise north nose note nothing notice
        novel nurse object occasion ocean office officer oil opinion
        opportunity orange order organization outcome oven owner pace pack
        package pain paint pair palace palm pan panic park parliament part
        partner party passage passion path patience patient pattern pause
        peace peak pear pencil penny pension percent performance period
        permission person perspective phase philosophy phrase physics piano
        piece pig pile pilot pin pink pipe pitch pizza plant plate platform
        player pleasure plenty pocket poem poet point pole police policy
        politics pollution pool population position possession pot potato
        pound power practice praise prayer pressure price prince princess
        print priority prize procedure process product profession professor
        profit program progress project promise proof property proposal
        protection protest purpose push quality quarter queen quest queue
        quote race radio rain range rate reaction reading reality reason
        receipt reception recipe record recovery region relation
        relationship relief religion remark remedy rent repair repeat
        replacement reply report republic reputation request requirement
        research reserve residence resource respect response responsibility
        rest restaurant result retirement return revenue review revolution
        reward rhythm right ring rise risk river rock role roof root rope
        routine row rubber rule rush sad safety sail salad salary sale salt
        sample sand satisfaction sauce scale scene schedule scheme science
        scissors score sea season seat secret section sector security seed
        selection self sense sentence series service session setting shade
        shadow shame shape share sheep sheet shelf shell shift shine ship
        shirt shock shoe shop shore shorts shoulder shout show shower sight
        sign silence silk silver singer sink site situation size skill skin
        skirt sky sleep slice slide slip smell smile smoke snow soap sock
        sofa soil soldier solution somebody soul sound soup source south
        space speaker speed spell spider spirit split spoon sport spot
        spring square stable staff stage stair stamp star statement station
        status steak steal steam steel step stick stone stomach store storm
        story strain stranger strategy strength stress stretch string stroke
        structure struggle studio study stuff style subject substance
        success sugar suggestion suit summer sun supply support surface
        surgeon surprise survival suspect sweet swim swing switch symbol
        sympathy system table tail tale talent talk tank tap target task
        taste tax team tear technique technology telephone television
        temperature temple tension term test text theme theory throat ticket
        tie tiger tile till tip tire title toast toilet tone tongue tool
        tooth top topic tour tourist towel tower town toy track trade
        tradition traffic trail training transfer trash travel treat
        treatment tree trend trial triangle trick trip trouble truck trust
        truth tube tune tunnel turn twin type uncle union unit university
        valley value variety vegetable vehicle version victim victory view
        village violence vision visit voice volume vote wage wait wake walk
        wall wallet war warning wash waste watch wave wealth weapon weather
        web wedding weekend weight west wheel whisper wild will wind window
        wine wing winner winter wire wisdom wish witness wolf wonder wood
        wool worker worm wound wrap writer yard youth zone
        clothing accessories
        blouse skirt dress shirt pants trousers shorts coat jacket sweater
        hoodie vest scarf gloves socks boots sandals slippers heels sneakers
        uniform costume suit belt helmet glasses sunglasses jewelry
        necklace bracelet earring backpack purse umbrella raincoat pajamas
        robe apron pocket sleeve collar button zipper fabric cotton wool
        silk leather denim linen outfit wardrobe designer brand size
        fitting tailor laundry detergent hanger closet drawer shelf
        action verbs extension
        accept achieve acknowledge acquire adapt adjust admire admit adopt
        advance affect afford agree alert allow amuse analyze announce annoy
        apologize appreciate approach approve argue arrange arrive assess
        assist assume assure attach attack attempt attend attract avoid
        award balance bake bargain behave betray bid bite bless blink bloom
        boil borrow bounce bow breathe brew broadcast brush burst bury
        calculate campaign cancel capture celebrate challenge chase cheat
        chew cheer choose chop circle clap clean climb close collapse
        collect combine comfort command compare compete complain complete
        compose concentrate confirm connect conquer convince copy correct
        cough count crash crawl cross crush cry cure curl dare decorate
        delay deliver demonstrate deny depart deposit describe deserve
        destroy detect develop disagree disappear discover discuss disguise
        dismiss display distribute disturb dive divide drag drain draw
        drift drill drive drown dry dump dust earn echo edit educate
        eliminate embrace encourage entertain escape establish evaluate
        examine excite excuse expand expect explain explode explore export
        expose extend fail fade fasten fetch fill film finance fix flash
        float flood fold forbid force forecast forget forgive form found
        freeze frighten fry gather gaze generate glow grab graduate greet
        grin grind grip guess hang harm heal heat hesitate hide highlight
        hire hit hook hug hunt hurry identify ignore imagine impress
        improve include indicate inform injure insist inspect inspire
        instruct intend interrupt introduce invent invest invite involve
        join kneel knock label land launch lay leak lean leap lend lift link
        load locate lock long lower mail maintain manage manufacture march
        marry matter melt mend mention merge mind miss monitor motivate
        mount mourn multiply negotiate nod nominate observe obtain occupy
        occur offend operate oppose organize overcome owe paint park
        participate pass paste pat pay perform permit persuade pick pile
        pitch play polish pop pour praise pray preach predict prefer prepare
        present preserve press pretend prevent produce promise promote
        pronounce propose protect prove publish pull pump punch punish
        purchase quit raise reach react realize receive recognize recommend
        recover recruit recycle reduce reflect refuse register regret
        reject relate relax release rely remain remind remove repeat replace
        represent reproduce request require rescue resign resist resolve
        respect respond restore restrict retire reveal review revise ride
        rinse roar roast roll rotate rub ruin rush satisfy save scan scare
        scatter scream screw seal search seize select sell separate settle
        sew shake sharpen shave shelter shout shove shovel shrink shrug
        sigh simplify sing sketch ski slam slap slide snap snatch sneeze
        sniff snore sow spare spill spin spit spoil sponsor spray spread
        squeeze stab stain stamp stare steer sting stir stitch strengthen
        submit succeed suck suffer suggest suit support suppose surround
        survive swallow swear sweep swell tackle tap taste tease tighten
        touch trace translate tremble trust try tune twist unload unlock
        unpack update upgrade urge vacuum vanish vary volunteer wander warn
        weigh whistle widen wipe wrestle yawn yell zip zoom
        adjectives adverbs extension
        absent absolute absorbed abstract absurd abundant academic
        acceptable accessible accidental accomplished accurate accused
        active actual acute addicted adequate administrative adorable
        advanced adverse advised afraid aged aggressive agile ambitious
        ample angry annual anonymous anxious apparent appropriate arbitrary
        artificial ashamed asleep attractive authentic automatic awake aware
        awful awkward balanced bare basic beautiful bitter bland blank bold
        boring brave brief brilliant broad broken calm careful careless
        cautious charming cheerful chilly circular civic classical clever
        cloudy clumsy coarse comfortable common competitive complex
        comprehensive concerned confident conscious considerable consistent
        constant contemporary content continuous contrary convenient
        conventional cooperative correct costly courageous courteous cozy
        creative critical crowded crucial cruel cultural curious current
        cute damp dangerous dark dear decent decisive decorative deep
        defensive delicate delicious delightful dense dependent depressed
        desperate detailed determined different digital direct dirty
        disappointing discreet distant distinct diverse dizzy dramatic
        dusty eager early easy economic elaborate elegant eligible
        embarrassed empty endless enormous enthusiastic entire environmental
        equal equivalent essential evident evil exact excellent exceptional
        excessive exciting exotic expensive explicit extraordinary fair
        faithful familiar famous fancy fantastic favorable fearful fierce
        final financial flat flexible fond formal former fortunate fragrant
        free frequent fresh friendly frozen full fundamental funny generous
        gentle genuine giant glad gloomy gorgeous graceful grand grateful
        greedy grim hairy handy harmonious harsh healthy heavy helpful
        historic hollow holy honest hopeful horrible hostile huge hungry
        ideal identical ignorant imaginary immediate immune impressive
        inadequate inappropriate incredible independent indirect individual
        industrial inevitable infinite informal innocent innovative instant
        insufficient intact intelligent intense internal international
        invisible involved jealous joint keen legal legislative liberal
        likely literary lively logical lonely loose loud loyal lucky mad
        magical magnificent major mature maximum meaningful mental mere
        mild minimal minimum minor miserable mobile modern modest moist
        moral mutual mysterious naked narrow nasty native neat necessary
        negative nervous neutral noble normal notable numb numerous
        objective obvious odd offensive official open opposite optimistic
        optional ordinary organic original outstanding overall painful pale
        parallel particular passionate peaceful perfect permanent personal
        pessimistic physical plain pleasant pleased plentiful polite
        popular portable positive potential powerful practical precise
        predictable pregnant premature previous primary primitive principal
        prior private probable productive professional profound prominent
        proper proud provisional psychological punctual pure qualified
        quiet radical random rapid rare rational raw realistic reasonable
        recent regular relevant reliable reluctant remarkable remote
        representative resident resistant responsible rich ridiculous rigid
        rough round rural sacred satisfactory scientific secure selective
        sensitive separate severe shallow sharp shiny shocked short shy
        significant silent silly similar sincere single slight slim smooth
        social soft solar sole solid sophisticated spare specific
        spectacular spiritual splendid standard static steady sticky stiff
        still straight strange strict striking strong stubborn stunning
        stupid subjective substantial subtle successful sufficient sunny
        superior supreme sure suspicious swift sympathetic systematic
        technical temporary tender tense terrible thick thin thorough
        thoughtful tidy tight tiny tired tolerant tough traditional tragic
        tremendous trim tropical typical ugly ultimate unanimous uncertain
        unconscious unexpected unfair unique universal unlikely unpleasant
        unusual upper upset urban urgent useful usual valid valuable
        variable vast verbal vertical violent virtual visible visual vital
        vivid voluntary vulnerable warm weary weird wet wide widespread
        willing wise wooden worthy wrong young
        actually afterwards altogether approximately ashore aside backward
        barely besides clockwise meanwhile moreover namely nearby
        nevertheless onwards otherwise outset overhead overnight somehow
        somewhat thereafter thereby therein throughout upwards willingly
        society professions institutions
        academy administration agency ambassador analyst architect
        astronaut attorney auditor author baker banker barber biologist
        butcher carpenter cashier chef chemist clerk coach consultant
        contractor counselor dentist designer detective diplomat director
        editor educator electrician engineer entrepreneur executive farmer
        firefighter florist gardener geologist governor guard hairdresser
        historian inspector instructor interpreter janitor journalist
        librarian linguist magistrate maid mayor merchant miner musician
        novelist nutritionist optician painter pharmacist philosopher
        photographer physician physicist plumber politician postman
        principal producer programmer prosecutor psychiatrist psychologist
        publisher reporter researcher sailor scientist sculptor secretary
        senator sergeant shepherd solicitor surgeon surveyor tailor
        technician translator tutor veterinarian waiter welder zoologist
        accountant corporation enterprise charity committee council
        congress court embassy headquarters institute institution ministry
        municipality parliament laboratory observatory gallery museum
        theater stadium arena gymnasium cathedral chapel monastery cemetery
        monument memorial sculpture warehouse harbor port airport railway
        subway highway intersection tunnel canal dam refinery plantation
        orchard vineyard brewery grocery pharmacy clinic ward surgery
        courtroom prison consulate
        science nature
        atom molecule electron proton neutron nucleus cell chromosome gene
        protein enzyme bacteria virus vaccine antibody hormone insulin
        neuron lung liver kidney stomach intestine skeleton artery vein
        plasma tissue organ embryo fetus species evolution mutation
        ecosystem habitat biodiversity photosynthesis respiration metabolism
        digestion circulation galaxy universe planet solar asteroid comet
        meteor orbit gravity radiation spectrum wavelength frequency
        magnetic electric current resistance voltage circuit battery magnet
        laser prism lens telescope microscope satellite rocket shuttle
        launch landing climate weather humidity atmosphere pressure oxygen
        nitrogen carbon dioxide hydrogen helium methane ozone glacier
        volcano earthquake tsunami hurricane tornado typhoon drought flood
        avalanche erosion sediment mineral crystal granite limestone quartz
        diamond emerald ruby sapphire fossil dinosaur mammal reptile
        amphibian insect spider butterfly bee ant mosquito beetle worm
        eagle hawk owl sparrow pigeon crow dove swan duck goose penguin
        dolphin whale shark salmon tuna crab lobster shrimp frog toad
        lizard snake turtle crocodile elephant giraffe lion tiger leopard
        cheetah zebra monkey gorilla chimpanzee panda koala kangaroo camel
        horse cattle sheep goat pig rabbit squirrel hamster hedgehog fox
        wolf deer moose
        mathematics units
        geometry algebra arithmetic calculus equation formula theorem proof
        hypothesis variable coefficient constant percentage ratio
        proportion decimal fraction integer prime average median statistics
        probability graph chart axis diameter radius circumference triangle
        rectangle square cube sphere cylinder cone pyramid angle degree
        celsius fahrenheit kilogram gram liter meter kilometer centimeter
        millimeter mile inch foot pound ounce
        cet6 a through l
        abolish abound abrasive abrupt absentee absorb abundance abuse
        accelerate accent accessible acclaim accommodate accomplish accord
        accumulate accuracy accuse accustom acquaint addict addition
        adequate adhere adjacent adjoin administer admission adolescent
        adore adult advent adventure advocate aesthetic affair affection
        affiliate affirm afflict afloat agenda aggravate aggregate agitate
        agony agreeable agriculture aide aircraft album alien alienate
        align allegation allege allegiance allergy alleviate alliance
        allocate allowance alloy allure alter alternate altitude aluminum
        amateur amaze ambiguity ambulance amend amid ammunition ample
        amplify analogue ancestor anchor angel anguish animate ankle annex
        anniversary antenna anthem anthropology antic antique apartment
        apparel appetite applaud applause applicant appoint apprehend
        approximate aptitude arch archive arena arithmetic armament armor
        aroma arouse array arrest arrogant arrow articulate ascend
        ascertain ash assault assemble assert asset assign assimilate
        associate assurance astonish astronomy athlete attach attain
        attitude auction audit augment autonomous availability avenue
        aviation await awesome axis bachelor backbone bacterium badge
        baggage balcony bald ballot banal bankrupt banner banquet baron
        barracks barrier basement basin batch battlefield beacon bean
        bearing beast behalf beloved beneath beneficial benign bestow
        beverage beware bias billion bind biography bishop bizarre blanket
        blast bleak bleed bless bliss blossom blueprint blunder blunt blur
        blush boast bolt bomb bonus booth bore bounce boundary bouquet bout
        boiler brace bracket brake brand brave breach breadth breakdown
        breakthrough breed breeze bribe brick bride brink brisk brochure
        broker brook budget buffet bug bulk bulletin bully bunch bundle
        burden bureaucracy burial bust busy butter cabin cabinet cache
        cactus cafe cage calendar caliber cannon canvas cape capitalism
        caption captive carbon cardinal carve cascade casino cast casual
        casualty catalyst catastrophe cathedral cattle caution cease
        celebrity cement censor census central certificate chain chairman
        chalk chamber champion chancellor chaos charter cheap cheek
        cheerful chemistry cherish chip chord chore chorus chronic chunk
        cigarette cinema circulate circumstance cite citizen civic
        civilization clarify clarity clash clasp classify clause claw clay
        clergy click cliff climax cling clip clone closet cloth cloud clue
        cluster clutch coal coalition code cognitive coherent cohesion coil
        coincide collaboration collective collide collision colonel
        colonial column combat comedy comet commencement commend commerce
        commission commit commodity commonplace communist commute compact
        companion comparable comparative comparison compass compassion
        compel compensate competent compile complaint complement complexity
        compliance complicate compliment comply component composer
        composite compound comprehend comprise compromise compute conceal
        concede conceive concept conception concession conclude concrete
        condemn condense conduct confer conference confess confidential
        confine conflict conform confront confuse conjunction conquer
        conscience consecutive consent consequent conservation conserve
        considerate consist console consolidate conspicuous constellation
        constitute constitution constrain construct consult consumer
        consumption container contaminate contend context continent
        continual contradict contrast contribute controversial controversy
        convention converge conversation converse conversion convey convict
        conviction cooperate coordinate copper cord corn corporate
        correspond corridor corrupt costume cottage couch cough council
        counsel counter counteract counterpart countryside courtesy
        coverage covert crack cradle craft crazy creed creek crew cripple
        crisp criterion critique crop crude cruise crust cubic cue
        cultivate cumulative cunning curb curiosity curl currency
        curriculum curse cursor cushion customary cylinder daily dairy damp
        darling dash database dawn daytime dazzle deadline deadly deaf dean
        debate decay deceit deceive decimal deck declare decline decrease
        decree dedicate deduce deduct deem default defeat defect defendant
        defender defensive defer defiance deficiency deficit define
        definite definition defy degenerate delegate delete deliberate
        delight denial denounce dense density depart departure depend
        dependence depict deploy depress deprive deputy descend descendant
        descent description desert designate desirable despair desperate
        despise despite dessert destination destiny destruction destructive
        detach detect detector deter deteriorate determination detour
        devastate device devise devote devotion diabetes diagnose diagram
        dial dialect dialogue diameter dictate dictionary diet differ
        differential differentiate digest digital dignity dilemma diligent
        dim dimension diminish dine dioxide dip diplomacy diplomatic
        directive directory disability disable disadvantage disappear
        disappointment disapprove discard discharge discipline disclaim
        disclose discourse discreet discrete discretion discriminate
        disgrace disgust disk dislike disorder dispatch dispel dispense
        disperse displace disposal dispose disposition dispute disregard
        disrespect disrupt dissent dissolve ditch diverse divert divine
        divorce dock doctrine document documentation domain domestic
        dominant dominate donate donor dormitory dose dot double doubtful
        downtown dread drift drip driveway drown drum drunk dual duck due
        duel dull dumb duplicate durable duration dwell dynamic dynasty
        eager eagle earnest earnings ease eccentric eclipse ecology
        economical economics economist ecosystem edition editorial
        effectiveness efficiency ego eight eighteen eighth either eject
        elapse elastic elbow elder election electrical electronics element
        elevate eleven elite eloquent elsewhere embed embody emigrate
        emission emperor emphasis empire employment empty enable enact
        encounter encroach encyclopedia endanger endeavor endorse endurance
        enemy enforce engagement engineering enhance enlarge enlighten
        enrich enroll ensure enter enterprise enthusiasm entirety entitle
        entity envelope envy episode equality equator erase erect err
        erroneous erupt essay essence esteem estimate eternal ethic ethical
        ethnic evaluate evaporate eve eventual everlasting evidence evil
        evolve exaggerate exam exceed exceedingly excel excellence except
        exception exceptional excerpt excess excite exclaim exclude
        exclusion exclusive excursion execute execution exhaustive exhibit
        exile exist existential exit exotic expansion expedition expel
        expend expenditure expertise expire explicit exploit explosion
        explosive export exposure expressway extension exterior extinct
        extinction extra extract extremist fabric fabricate facet
        facilitate faculty fade faint fairy faith false fame famine
        fantastic fantasy farewell fascinate fast fatal fate fatigue fault
        favorite feasible feast federal federation feeble feedback fellow
        fellowship female ferry fertile fetch fiber fiction fierce fifteen
        fifth fifty finding finite fireman fiscal fist five fix flag flash
        flaw flee fleet flesh fling flock flour flourish fluent fluid flush
        foam focus fog fold folk folklore fond fool footprint forbid
        forehead foreigner forge format formation formulate forth forum
        forty fossil foster fountain fourteen fourth fraction fracture
        fragile framework franchise frank fraud freeze freight frequency
        friction friendship frog frontier frost frown frustrate fry fume
        function fund funeral furious furthermore fury fuse gain gallon gap
        garage garbage garlic gasp gate gauge gay gear gender gene
        generalize generator genetic genius genre gently genuine geography
        geology germ gesture ghost gifted gigantic glance glare gleam glide
        glimpse global globe gloss glue golden golf goodbye goose gorgeous
        govern gown grace gracious gradual grain grammar grand grant graph
        graphic grasp grateful grave gray great greedy green greeting grid
        grief grill grim grocery gross grove guarantee guess guidance guilt
        gulf habitat halt hamburger handout handful handwriting handy
        harden hardly hardship hardware harmony harness haste hasty hatred
        haul hawk hazard headline heal heap heaven hedge heel heir
        helicopter helmet helpful helpless hen hence herb herd heritage
        herself hierarchy hint hire holy homework honey honor horizon
        horizontal horn hospitality host hostility hotel household housing
        hover hug huge human humble humid humor hundred hunger hunt hurt
        husband hut hybrid idea ideology idle idol ignite ignorance ill
        illegal illness illuminate illustrate imbalance imitate immediate
        immense immigrant immigration immune impair impart impartial
        imperative imperial implementation implication implicit impose
        impossible imprisonment impulse inadequate inaugurate inch
        incidentally incline inclusive incorporate incredible incur
        independence index indication indicative indicator indoor induce
        indulgence infant infect infer inferior inflict influential inform
        infrared infrastructure ingenious inhabit inhabitant inherent
        inherit inhibit initial initiate initiative inject injection
        injustice inn inner innovate input inquire insect insert insight
        installation instantaneous instead instigate instruct instrumental
        insulate insult insure intact integrate integrity intellectual
        intelligence intent interact interfere interior intermediate
        interpret interval intervene intervention intimate intimidate
        intricate intrigue intrinsic intrude intuition invade invalid
        invaluable invasion inventory investigate investor invisible invoke
        inward ironic irony irrational irregular irrelevant irrigate
        irritate isolate isolation itemize itinerary jail jazz jet jewelry
        jog jolly journalism joy judgment junction jungle junior junk jury
        just justice justify juvenile kernel keyboard kidnap kidney kilo
        kindergarten kingdom kit kitten kneel knight knit knot knowledge
        label labor lace lack lad ladder laden lag lamb landing landlord
        landscape lane lantern lap lapse largely laser lately latent lateral
        latitude latter laughter laundry lavish lawn layout lazy leadership
        leading leaf leak learning lease least leather lecture legacy legend
        legendary legislation legislature legitimate lemon lengthy lens
        lessen lest letter lever levy liable liberal liberate liberty
        license lid lieutenant lifetime lighten lighting likelihood likewise
        limitation limited linear linen linger lion liquid liquor listen
        literacy litter little lively liver living loan lobby locality
        locomotive lodge lofty log lone longing loop loose loosen lord lorry
        loss lot lounge lovely lover low loyalty luggage lumber lump lung
        lure luxury
        cet6 m through z
        machinery magic magical magistrate magnetic maid mainland
        maintenance majesty majority male mall management mandate maneuver
        manifest manipulate mankind manual manuscript marble margin
        marginal marine marketing masterpiece mat mate materialism
        mathematician mathematics matter mature may maybe mayor meal
        meaningful means meantime measurement mechanic mechanism medal
        mediate medical medieval medium meeting melody membership memorial
        mend mental mentor mercy mere merge merit mess message messenger
        meter methodology metric midnight midst might migrate migration
        mild military mill million mind mine minority minus miracle mischief
        miserable misery misfortune mislead missile missing mist mistaken
        mister mixture moan mobilize mock mode moderate modernize modest
        modify module moist mold molecular momentum monarch monday monk
        monopoly monster monthly mood morale mortality mortgage mosquito
        mostly mote motel moth motion motivate motivation motive mount
        mourn mouse mouth move movement much mud multiple multiply multitude
        municipal murder murmur musical musician must mute myself mystery
        myth nail naked namely nap napkin national nationality natural
        naval navigate navy near nearby nearly neat necessarily necessity
        neck needle neglect negotiation neighborhood nephew nest network
        neutral never nevertheless newly nickname niece nightmare nine
        nineteen ninety ninth nitrogen no nobody nod noisy none nonetheless
        nonsense noon nor norm normally north northern notable notebook
        notion notorious noun nourish novelty nowadays nowhere nuclear
        nuisance numb nursery nut nutrition nylon oak oath obey objection
        objective obligation oblige obscure observation observer obsolete
        obstacle occurrence odd odds odor of off offense offensive office
        officer offset offspring often okay old omit on once oneself onion
        online only onto opening opera operation operational operator
        opponent opportunity oppose opposition opt optical optimum option
        oral orbit orderly ore organism organization organize orient
        oriental orientation origin originate ornament orphan other
        otherwise ought our ours ourselves out outcome outdoor outer outfit
        outgoing outlet outline outlook output outset outside outstanding
        outward oval over overall overcome overflow overhead overhear
        overnight oversee overturn overwhelm owing owl owner ownership
        oxide oxygen pacific package packet pact page painful painter
        painting pair pale pan panel panic pant paperback parachute parade
        paradise paradox paragraph parallel paralyze parameter parcel
        pardon parent parenthesis parliament partial participant
        participation particle particularly parting partnership pass
        passenger passive passport past paste pastime pastor pat patch
        patent path patriot patrol pave pavement paw payment peaceful pear
        pearl peasant pebble pedal pedestrian peel peep peer penalty
        penetrate peninsula penny people pepper per perceive perception
        perch perfection perhaps periodic periodical perish permanent
        permission perpendicular perpetuate perplex persecute persevere
        persist persistence personal personality personnel persuade
        persuasion pessimist pest petition petrol petty phase photo
        photograph photographer phrase pickup picnic pie pierce pigeon pill
        pillar pinch pine pint pioneer pipe pistol pit pity plague plaintiff
        plan plane planet plaster plastic plausible playground plea plead
        pleasant please pleasure pledge plentiful plight plot plow plug
        plunge plural plus poem poetry pointed poison pole polish polite
        political politician politics poll pollute pond poor popularity
        porch pork porridge portable porter portion portrait portray pose
        positive possess possibility possible post postage poster postpone
        pot potential pour poverty powder powerful practically practise
        pray prayer preach precaution precede precedent precious precise
        precision preclude predecessor predict prediction predominant
        prefer preferable preference prefix pregnancy prejudice preliminary
        premier premise premium preparation preposition prescribe
        prescription presence presently preservation presidency president
        pressure presume presumably pretend pretty prevail prevent previous
        prey priest primary prime prince princess principle print prior
        priority prison prisoner privacy privilege probable probably probe
        problem procedure proceed procession proclaim production
        productivity proficiency proficient profile profitable profound
        progress progressive prohibit projection promise promotion prompt
        pronunciation proof proportion proposal proposition prosecute
        prospect prosperity prosperous protect protein protest protocol
        prototype proud provide provided province provision provisional
        provoke psychological publicity pulse pump punctual punishment
        pupil purchase purely purify purity purple purse pursue pursuit put
        puzzle qualify quantity quarrel quart quarterly queen queer query
        question questionnaire quick quiet quilt quit quiz quota radiation
        radical radioactive radius rage raid rail railroad rainbow rake
        rally ranch random rank rarely rash rat rather rating rational raw
        ray razor react reader readily ready realism realm reap rear
        reasonable rebel rebellion recall recede receipt recently reception
        receptionist recess recipient reciprocal recite reckon recollect
        recommendation reconcile recorder recreate rectangle rectify recur
        recycle red reduction redundancy redundant reed reef refer reference
        refine reflection reflex reform refrain refresh refreshment refuge
        refugee refund refusal refute regarding regardless regime register
        regret regular regulate regulation rehearse reign reinforce rejoice
        relation relative relax relay relevant reliability reliance relieve
        religion religious reluctant remain remainder remark remedy remember
        remind remnant remote removal render renew repay repeatedly repent
        repetition replace representation reproduce republican reputation
        requirement resemble resent reservation reservoir reside residential
        resign resist resistance resistant resolution resort resource
        respective respond resultant resume retail retain retirement retort
        retrieve reunion revelation revenge reverse revise revive revolt
        revolutionary rhythm rib ribbon rice rid riddle ridge ridiculous
        rifle rigid rigorous rim ring riot ripe rival roar rob robe robot
        robust rocket roll romantic roof root rouse route royal royalty rub
        rubber rubbish rude rug ruin ruler rumor runner rural rust ruthless
        sack sacred sacrifice saddle safe sail sailor saint sake sale
        salesman salute sample sanction sandwich sane sang satellite satire
        satisfy saturate saturday sauce saving saw scale scan scandal scarce
        scatter scenery schedule scholar scholarship scientific scope scorn
        scout scramble scrap scrape scratch screen script scrutiny sea seal
        season seat secondary secret section sector see seed seek seeming
        segment seldom selection selfish semester semicolon senate send
        senior sensation sensible sensitive sentiment sequence serious
        sermon servant session set setting settlement seven seventeen seventh
        seventy several sew sewage sex shaft shall shallow sham shame sharp
        shatter she shear shed sheer sheet shield shine ship shortage
        shorthand shortly shot should shoulder shove shrink shrug shut
        shuttle shy sick sidewalk siege sigh signal signature significance
        signify silent similarity simplicity simplify simulate simultaneous
        sin since singer singular sir sit six sixteen sixth sixty skate
        skeleton skeptic ski skilled skillful skim skip sky slam slaughter
        slave slavery sleeve slender slice slight slim slip slipper slippery
        slogan slope slot slow smart smash smell smooth smother snack snake
        snap snatch sneak sneeze sniff snow soak soap soar soccer socialism
        sock soda soft software solar solemn solidarity solidify solitary
        solo solution solve somebody someone sometime sometimes somewhere
        son soon sophisticated sort sound sour source southeast southern
        southwest sovereign spacecraft spark sparkle speak special
        specialize specialist specialty species specification specify
        specimen spectacle speculate speech speed sphere spine spirit
        spiritual spite splash splendid split spontaneous spoon sportsman
        spouse spray springtime sprinkle spur spy squad squeeze stability
        stack stadium stage stain staircase stake stale stamp standpoint
        star stare startle starve state statesman static statistical statue
        statute stay steady steak steep steer stem stereo stereotype stern
        stiff still stimulate sting stir stock stoop stop storage stout
        stove straight strait strange strap strategy straw stream strengthen
        stress strict stride strike stripe strive stroll structure stumble
        stun stunt stupid subjective submerge submit subordinate subscribe
        subsequent substitute subtle suburb subway succeed succession
        successive such sudden sue sufficient sum summarize summary summit
        sun sunday sunrise sunset sunshine superb superficial superior
        supervise supper supplement suppose suppress supreme surely surgery
        surname surpass surplus surround survey survival suspect suspend
        suspicion suspicious sustain swallow swamp swap swear sweat sweater
        sweep sweet swift sword symmetrical symphony symptom syndrome
        systematic tablet tactic tag take tale tall tame tan tape tariff
        task teach teacher team tease technical technician tedious teenager
        telegram telescope tell temper tempo temporary tempt ten tenant
        tend tendency tender tennis tense tent tentative terminal terminate
        terrace terrain terrible terrific terrify territory terror testimony
        textbook textile than thank that the theft theme themselves then
        therapy there thereafter therefore thermal these thesis they thick
        thief thin thing think third thirst thirteen thirty this thorough
        those though thought thousand thread threat three threshold thrive
        throat throne through throughout throw thrust thumb thunder thus
        ticket tidy tie tight tile timber timely timetable tin tiny tip
        tired tissue title to tobacco today toe together toilet token
        tolerance tolerate toll tomato tomb tomorrow tongue tonight too
        tool top torch torrent torture toss total touch tough tourism
        tournament tow toward tower toy trace tract trademark tragedy trail
        trait traitor tram tramp transaction transcript transfer transform
        transformation transient transistor transit transition translate
        translation transparent transplant transport transportation trap
        trash tray treasure treat treaty tree trench trend tribe tribute
        trick trickle trigger trim trip triumph trivial trolley troop
        tropical troublesome truck true trumpet trust try tube tuck tug
        tuition tumble tune turbine turbulent turn tutor twentieth twenty
        twice twin two typewriter typical ultimately umbrella unable
        unacceptable unaware uncertain unclear uncover under undergo
        undergraduate underground underline undermine underneath
        understanding undertake undo undoubtedly uneasy unemployment
        unexpected unfair unfold unfortunate unhappy uniform unify unique
        unite unity universal unknown unless unlike unlikely unload until
        up update uphold upon upper upright upstairs upward urge us usage
        used user usher utility utilize utmost utter vacant vacation
        vaccinate vacuum vague vain valve van variable variation various
        vary vase vast veil vein velocity velvet vendor ventilate venture
        verb verify version vertical vessel veteran veto via vibrate vice
        video viewpoint vigorous village vinegar violate violent violin
        virtual virtue vocabulary vocal vocation void volcano volleyball
        volt voluntary voyage vulgar vulnerable wagon waist waiter wake
        wander want ward wardrobe ware warehouse warfare warm warmth warn
        warrant warranty waterproof wax we weak weaken wealthy weapon wear
        weary weave wedge weed week weekday weekly weep weigh welcome weld
        welfare well west western wet whale what whatever wheat when whenever
        where whereas wherever whether which whichever while whip white who
        whoever whole wholesale wholesome whom whose why widow width wife
        win wind wink wink wipe wire wit with withdraw withhold within
        without withstand woman wonderful wooden word workforce workman
        workshop worldwide worse worship worst worth worthless worthy would
        wound wreck wrist writing wrong yard yawn year yearly yell yellow
        yes yet yield you your yours yourself zealous zero zigzag zinc zoo
        toefl academic vocabulary
        aboriginal abstraction academia accreditation accumulation
        acknowledgment adaptation adherence agility alignment allocation
        amplification annotation anomaly appropriation approximation
        articulation attribution authentication authorization benchmark
        calibration categorization circumference clarification cognition
        cohesion compatibility compilation comprehension condensation
        configuration confirmation conformity confrontation connectivity
        connotation consensus consolidation constraint consultation
        contamination continuum contradiction convergence correlation
        credibility deduction differentiation dimension dissemination
        distortion diversification elaboration elevation elimination
        embodiment emergence emulation encapsulation enhancement
        entitlement enumeration equilibrium escalation estimation
        exacerbation excavation exemplification exploitation extrapolation
        fabrication facilitation familiarization fluctuation formulation
        fragmentation generalization heterogeneous homogeneous
        identification implementation inauguration incorporation
        indifference induction inference inhibition initiation inspection
        institutionalization intensification interdependence intersection
        introspection inversion irradiation juxtaposition legitimation
        liquefaction mediation migration modification normalization
        notation notification obliteration obstruction optimization
        oscillation overestimation paradigm perpetuation precipitation
        predisposition presupposition proclamation proliferation
        quantification reconciliation redistribution rehabilitation
        reinforcement reorganization replication reproduction retrieval
        sedimentation segregation simulation specification stabilization
        standardization stratification sublimation substitution
        superposition suspension sustainability transformation
        transmission transplantation underestimation uniformity
        urbanization utilization verification volatilization
        anthropology archaeology biochemistry botany cartography
        climatology cosmology cryptography epidemiology ethnography
        etymology geomorphology glaciology historiography hydrology
        ichthyology immunology linguistics meteorology microbiology
        mineralogy morphology neuroscience oceanography ornithology
        paleontology pharmacology physiology sociology topology volcanology
        business finance extension
        acquisition affiliate amortization arbitrage brokerage
        capitalization collateral consortium debit depreciation derivative
        dividend equity escrow foreclosure futures goodwill hedge holding
        inflation insolvency invoice leverage liquidation liquidity margin
        merger portfolio procurement profitability prospectus rebate
        receivable recession shareholder solvency speculation stockholder
        subsidy turnover valuation volatility yield actuary broker financier
        proprietor stockbroker trader underwriter annuity audit bailout
        bankruptcy beneficiary charter concession coupon creditor customs
        deed deposit discount dowry earnings embargo endorsement estimate
        franchise freight guarantee indemnity indenture insurer kickback
        ledger lease lien markup monopoly overhead payable payroll premium
        principal quotation receipt refund remittance royalty securities
        stake syndicate tender transaction treasury voucher warrant
        wholesale
        emotions feelings
        admiration adoration affection agitation alienation amusement
        anguish annoyance apathy apprehension arousal awe bitterness bliss
        boredom calmness compassion contempt contentment defeat delight
        desperation disbelief disorientation distress dread eagerness
        ecstasy embarrassment emptiness enchantment exasperation fury
        gloominess gratitude grief happiness hopefulness hopelessness
        humiliation hysteria infatuation insecurity insult irritation
        isolation jubilation loneliness melancholy mortification nostalgia
        outrage panic pity rage remorse resentment resignation sadness
        serenity sorrow spite suffering thrill tranquility uneasiness wrath
        zeal
        countries cities languages
        argentina australia austria bangladesh belgium bolivia botswana
        brazil bulgaria cambodia cameroon chile colombia croatia cuba
        cyprus denmark ecuador egypt ethiopia finland ghana guatemala
        honduras hungary iceland indonesia iran iraq ireland israel
        jamaica jordan kazakhstan kenya kuwait kyrgyzstan laos latvia
        lebanon libya lithuania luxembourg madagascar malawi malaysia
        maldives mali malta mauritius mexico monaco mongolia montenegro
        morocco mozambique myanmar namibia nepal nicaragua niger nigeria
        norway oman pakistan palau panama paraguay peru poland portugal
        qatar romania rwanda samoa senegal serbia seychelles singapore
        slovakia slovenia somalia sudan suriname sweden switzerland syria
        tajikistan tanzania thailand timor togo tonga trinidad tunisia
        turkmenistan tuvalu uganda ukraine uruguay uzbekistan vanuatu
        venezuela vietnam yemen zambia zimbabwe andorra albania armenia
        azerbaijan bahamas bahrain barbados belarus belize benin bhutan
        brunei burundi chad comoros congo djibouti dominica eritrea
        estonia fiji gabon gambia georgia grenada guyana haiti kiribati
        kosovo lesotho liberia liechtenstein mauritania moldova nauru niue
        beijing shanghai guangzhou shenzhen chengdu hangzhou wuhan xian
        nanjing chongqing tianjin suzhou qingdao dalian harbin shenyang
        tokyo osaka kyoto seoul busan bangkok hanoi jakarta manila delhi
        mumbai bangalore chennai kolkata karachi lahore dhaka kathmandu
        moscow london paris berlin rome madrid vienna prague amsterdam
        brussels athens lisbon dublin stockholm oslo helsinki warsaw
        budapest bucharest belgrade zagreb kyiv minsk tbilisi yerevan baku
        astana cairo nairobi lagos accra addis kampala kigali tunis
        algiers casablanca johannesburg toronto vancouver montreal bogota
        lima santiago quito caracas havana kingston washington york chicago
        houston phoenix philadelphia antonio diego jose austin jacksonville
        francisco columbus indianapolis charlotte seattle denver nashville
        oklahoma louis portland memphis milwaukee albuquerque tucson
        fresno sacramento mesa atlanta omaha colorado raleigh miami oakland
        minneapolis tulsa cleveland wichita arlington newark buffalo plano
        henderson lincoln orlando jersey chesapeake norfolk fremont
        garland irving hialeah richmond boise
        spanish french german italian portuguese russian arabic hindi
        bengali turkish persian swedish norwegian danish dutch polish czech
        hungarian greek hebrew thai vietnamese korean japanese mandarin
        cantonese english american canadian mexican brazilian argentinian
        colombian chilean peruvian ecuadorian bolivian venezuelan uruguayan
        paraguayan british irish scottish welsh belgian swiss austrian
        finnish icelandic romanian bulgarian serbian croatian bosnian
        albanian macedonian montenegrin slovenian slovakian lithuanian
        latvian estonian belarusian ukrainian georgian armenian azerbaijani
        kazakh uzbek afghan pakistani indian bangladeshi nepali maldivian
        cambodian laotian burmese indonesian malaysian filipino mongolian
        australian zealand egyptian moroccan algerian tunisian libyan
        sudanese ethiopian kenyan tanzanian ugandan rwandan nigerian
        ghanaian senegalese cuban jamaican haitian guatemalan honduran
        salvadoran nicaraguan panamanian
        sports hobbies leisure
        athletics archery badminton baseball basketball bowling boxing
        cricket cycling darts fencing gymnastics handball hockey judo
        karate lacrosse marathon netball polo rowing rugby sailing shooting
        skating skiing snowboarding soccer softball squash surfing swimming
        taekwondo volleyball weightlifting wrestling yoga pilates hiking
        jogging camping fishing hunting climbing kayaking rafting diving
        snorkeling skateboarding parkour aerobics referee umpire teammate
        opponent defender striker goalkeeper midfielder forward substitute
        penalty offside halftime overtime trophy medal podium championship
        tournament fixture playoff chess poker bridge mahjong sudoku
        crossword riddle trivia bingo billiards snooker pinball arcade
        console gaming photography painting drawing sketching sculpting
        pottery knitting crochet sewing embroidery quilting woodworking
        gardening birdwatching stargazing reading writing blogging vlogging
        podcasting music dancing singing guitar piano violin drums flute
        saxophone trumpet cello harp ukulele banjo harmonica accordion
        orchestra choir band
        legal politics government
        allegation amendment appeal arrest bail bailiff bench brief
        causality claim clause constitution courtroom custody deposition
        dismissal felony hearsay indictment injunction jurisdiction lawsuit
        litigation misdemeanor motion ordinance plaintiff precedent
        prosecution verdict affidavit subpoena testimony tort statute
        legislation regulatory compliance arbitration mediation negotiation
        settlement damages negligence malpractice embezzlement bribery
        extortion perjury larceny burglary homicide manslaughter assault
        battery trespass trademark confidentiality nondisclosure
        incorporation fiduciary debtor foreclosure probate testament
        alimony prenuptial adoption guardianship emancipation asylum
        deportation naturalization referendum ballot constituency delegate
        caucus nomination impeachment acquittal conviction amnesty parole
        incarceration restitution judiciary magistrate legislature senator
        congressman governor mayor alderman sheriff marshal diplomat
        consulate treaty ratification sovereignty sanction embargo
        nationalism federalism socialism communism capitalism liberalism
        conservatism libertarian democracy republic monarchy dictatorship
        oligarchy aristocracy bureaucracy technocracy plutocracy theocracy
        anarchy coup uprising rebellion revolution protest demonstration
        strike boycott lobby
        education academia
        alumni assignment attendance auditorium bachelor commencement
        doctorate dropout elective enrollment fellowship freshman honors
        internship postdoctoral professorship provost registrar tenure
        transcript undergraduate valedictorian literacy numeracy pedagogy
        syllabus dormitory cafeteria campus recitation colloquium seminar
        symposium conference workshop practicum externship apprenticeship
        certification accreditation diploma degree scholarship grant
        research thesis dissertation defense examination quiz midterm
        homework project presentation lecture tutorial mentor advisor
        principal superintendent board trustee faculty staff administration
        bursar chaplain counselor librarian coach dean rector chancellor
        president
        travel transportation
        accommodation admission airline airstrip backpack baggage bicycle
        boat boulevard bridge brook canal carriage cart causeway checkpoint
        cockpit compartment corridor cruise deck departure destination
        detour driveway embassy expedition expressway fare ferry freeway
        gateway hostel itinerary jet journey junction landmark lane layover
        locomotive luggage metro milestone motel navigation overpass
        parking passport pathway pavement pedestrian pier platform railway
        resort runway scenic scooter sidewalk skyline subway suite terminal
        timetable toll tram transfer transit trolley turnaround underpass
        vessel visa voyage yacht
        health medicine body
        abdomen allergy ambulance anemia anesthesia antibiotic artery
        arthritis bandage biopsy bladder bloodstream bone brain breast
        breath capsule cartilage chemotherapy colon diagnosis digestion
        disease disorder dose epidemic fever fracture germ gland healing
        heartbeat herb immunity infection inflammation injection injury
        insulin intestine joint kidney ligament liver lung lymph metabolism
        muscle nerve nutrition ointment organ pancreas pathology pharmacy
        physician pill placebo plasma prescription prostate pulse
        quarantine rehabilitation remedy retina surgery syndrome therapy
        tissue tonsil transplant trauma treatment tumor ultrasound
        vaccination vaccine vein vertebra vitamin wound xray
        food cooking kitchen
        almond appetizer apricot asparagus avocado bacon bagel banana
        barbecue basil bean beef beet berry biscuit blackberry blueberry
        broccoli broth brownie brunch burrito butter cabbage cake calorie
        candy cantaloupe caramel carrot cauliflower celery cereal cheese
        cheesecake cherry chicken chili chip chocolate chopstick cider
        cinnamon coconut cocoa coffee cookie coriander corn crab cracker
        cranberry cream crepe crisp croissant cucumber cuisine cupcake
        curry custard dessert dimsum donut dough dressing dumpling eggplant
        entree fig flour fondue frosting garlic ginger grape grapefruit
        gravy guava ham hamburger honeydew horseradish icing jam jelly
        kale ketchup kiwi lasagna leek lemon lemonade lentil lettuce lime
        lobster lollipop lychee macaroni mango maple marinade marmalade
        marshmallow meatball melon meringue milkshake mint muffin mushroom
        mustard mutton noodle nutmeg oatmeal omelet onion oregano oyster
        pancake papaya parsley parsnip pasta pastry peach peanut pear pecan
        pepper pepperoni persimmon pickle pie pineapple pistachio pizza
        plum pomegranate popsicle pork porridge potato pretzel pudding
        pumpkin quiche radish raisin raspberry ravioli recipe roast
        rosemary rye saffron salad salami salmon salsa sandwich sausage
        sesame shrimp soup sorbet souffle soybean spinach sponge squash
        steak stew strawberry sugar syrup tangerine tart toast tofu tomato
        tortilla truffle turkey turnip vanilla veal vegetable venison
        vinegar waffle walnut wasabi watermelon wheat yogurt zucchini
        household daily objects
        appliance armchair ashtray attic awning balcony basement bathtub
        blanket blender blinds bookcase bookshelf broom bucket bulletin
        cabinet candle carpet cellar chandelier closet coaster couch
        countertop cupboard curtain cushion cutlery dishwasher doormat
        drawer dresser dryer dustpan fireplace freezer frying grill hamper
        heater humidifier kettle lamp lantern laundry lightbulb mattress
        microwave mirror mop napkin ornament oven pan pillow plug plumber
        plunger porcelain rack radiator razor remote rug scissors shelf
        shower sink soap sofa sponge stool stove suitcase sweeper tabletop
        teapot thermometer thermostat tissue toaster towel trash vacuum
        vase wardrobe washing
        landscape weather
        archipelago bay beach bluff bog canyon cape cliff coast creek delta
        desert dune estuary fjord forest geyser gorge grove heath highland
        hillside hollow island isthmus jungle lagoon lake lava marsh meadow
        mesa mountain oasis ocean peak peninsula plain plateau pond prairie
        promontory reef ridge riverbank savanna shore spring strait summit
        swamp terrace tundra valley waterfall wetland woods blizzard breeze
        cloud cyclone drizzle drought fog frost gale hail haze heatwave
        humidity hurricane lightning mist monsoon overcast rainbow sleet
        snowfall storm sunshine thunder tornado typhoon whirlwind wind
        colors materials shapes
        amber aqua azure beige black bronze burgundy cerise chartreuse
        crimson cyan ebony fuchsia gold gray green indigo ivory khaki
        lavender lilac lime magenta maroon mauve navy ochre olive orange
        peach pink purple red rose ruby salmon scarlet silver tan teal
        turquoise violet white yellow brass ceramic clay concrete copper
        cork cotton fabric fiber fiberglass foam glass granite latex leather
        linen marble metal nylon paper plaster plastic porcelain rubber
        silk steel stone titanium velvet vinyl wood wool circle cone cube
        cylinder diamond ellipse hexagon octagon oval pentagon polygon
        prism pyramid rectangle rhombus sphere spiral square triangle
        trapezoid
        communication media
        acronym algorithm analog annotation appendix archive article
        bibliography blog bookmark broadcast bulletin byte cable caption
        catalog channel chat clipboard codec commentary console copyright
        dashboard database diagram dial digital download ebook edition
        editorial emoji encyclopedia episode fax firmware font footer
        footnote forum gigabyte glossary hashtag headline hyperlink
        infographic interface internet intranet joystick keyword laptop
        lecture livestream magazine mailbox malware media memo metadata
        modem monitor multimedia newsletter notification online pamphlet
        paragraph password pixel platform podcast portal poster printer
        publication radio receiver router satellite scanner screenshot
        signal smartphone software speaker spreadsheet stream subscriber
        tablet telegraph television terminal thumbnail toolbar transcript
        tutorial typewriter upload username vlog voicemail webcam webpage
        wifi wireless
        academic verbs adjectives
        abbreviate abdicate abhor abide abstain accentuate accredit
        acculturate adjudicate admonish adorn aggrandize alleviate ambulate
        antagonize appease arbitrate ascertain assuage attest authenticate
        corroborate culminate decimate delineate demarcate demote denigrate
        derogate dichotomize differentiate dilute disambiguate dissect
        extrapolate hypothesize infer interpret extrapolate postulate
        qualify quantify substantiate validate synthesize scrutinize
        empirical theoretical conceptual hypothetical tentative plausible
        credible feasible viable sustainable scalable quantifiable
        measurable observable verifiable falsifiable reproducible
        replicable longitudinal qualitative quantitative nominal ordinal
        categorical discrete continuous binary nonlinear exponential
        logarithmic asymptotic stochastic deterministic heuristic iterative
        recursive inductive deductive abductive causal correlational
        spurious robust fragile resilient elastic plastic viscous porous
        permeable soluble inert volatile acidic alkaline synthetic
        cognitive behavioral affective intrinsic extrinsic innate acquired
        hereditary contextual situational particular tangible intangible
        explicit implicit tacit overt covert manifest latent chronic
        malignant contagious infectious endemic pandemic symptomatic
        asymptomatic acute severe moderate mild benign reversible
        irreversible permanent transient periodic aperiodic symmetric
        asymmetric isotropic anisotropic miscible immiscible flammable
        combustible corrosive toxic carcinogenic biodegradable renewable
        nonrenewable finite infinite bounded unbounded convergent divergent
        monotonic convex concave differentiable integrable computable
        decidable tractable intractable optimal suboptimal minimal maximal
        local global relative absolute conditional unconditional necessary
        sufficient contingent probabilistic normative descriptive
        prescriptive explanatory exploratory confirmatory experimental
        observational retrospective prospective randomized blinded placebo
        controlled systematic rigorous stringent scrupulous meticulous
        painstaking thorough exhaustive comprehensive inclusive sweeping
        insightful perceptive astute shrewd sagacious prudent judicious
        discerning discriminating penetrating incisive trenchant cogent
        compelling persuasive convincing forceful weighty telling
        verb inflections ed ing s forms
        accepted accepting accepts achieved achieving achieves added adding
        adds admitted admitting admits adopted adopting adopts advanced
        advancing advised advising affected affecting affects agreed
        agreeing agrees allowed allowing allows analyzed analyzing announced
        announcing answered answering answers appeared appearing appears
        applied applying applies appointed appointing approached approaching
        approved approving argued arguing arranged arranging arrested
        arresting arrived arriving asked asking asks assembled assembling
        assisted assisting assumed assuming assumed assuming attached
        attaching attacked attacking attempted attempting attended attending
        attracted attracting avoided avoiding awarded awarded backed backing
        baked baking balanced balancing banned banning bargained bargaining
        based basing begged begging began beginning begun behaving behaved
        belonging belongs bent bending bet betting begged begging believed
        believing belongs bent bending betrayed betraying biased biasing
        blamed blaming blessed blessing blocked blocking blown blowing
        boiled boiling booked booking boosted boosting borrowed borrowing
        bothered bothering bounced bouncing bowed bowing boxed boxing
        braked braking branched branching breathed breathing bred breeding
        brightened brightening broadened broadening brushed brushing
        budgeted budgeting bumped bumping burned burning burst bursting
        buried burying burned burning burst bursting
        plural forms batch one
        ages animals arms balls bands banks bars baths beaches bears beds
        bells belts benches birds birthdays bits blocks boards boats bodies
        bones boxes boys brains branches bridges bubbles budgets buildings
        buttons camps candles caps captains cards cases cats caves ceilings
        cells cents chances charges chats cheeks cheeses chickens chiefs
        chins choices churches circles coats coins collars colleges colors
        combs companies concerts contacts contests controls cooks corners
        costs counts couples courses cousins covers cows crashes credits
        crimes crowds crowns cultures cups curves customs dances dangers
        deals deaths debts decisions deer degrees delays demands depths
        deserts designs desks details differences difficulties directions
        disasters discounts discussions diseases distances districts
        divisions doctors dogs dollars donations doubts drafts dramas
        dreams dresses drivers drops drums duties edges effects efforts
        elevators emergencies emotions employees employers ends engines
        entrances errors events exchanges exercises exhibitions exits
        experiences experiments experts expressions eyes faces factories
        fans farms fashions favors fears feathers features fees feelings
        fences fevers fields fights figures fingers fires firms flags
        flames flavors flights floors flows flowers forks forms fortunes
        foundations fountains frames freedoms funerals gardens gates gifts
        girls gloves goals grades grains grounds groups guards guests
        guides guitars guns habits halls hands hats heads hearts heats
        heels heights heroes hills hips holes holidays hopes horns hotels
        identities images impacts imports incidents increases industries
        influences ingredients injuries insects inspections instances
        instruments intentions interests interviews inventions investments
        invitations islands items jackets jars jaws jobs joints jokes
        journeys judges keys kids kings knees knives knots labs ladies
        lakes lamps lands laws lawyers layers leaders legs lengths lessons
        levels libraries lifts lights limits lines lions lips lists locks
        logs machines magazines managers maps marks markets masters
        matches materials meals measures medicines members memories menus
        metals methods minds ministers mirrors missions mistakes mixes
        models moments monitors moods mountains mouths movements muscles
        museums nails nations necks neighbors nerves nests nets newspapers
        noises noses notes notices novels nurses objects occasions oceans
        offices officers oils opinions oranges orders outcomes owners packs
        packages pains paints pairs palms pans parks parts partners parties
        passages paths patterns pauses peaks pencils pensions periods
        permissions persons phases phrases pianos pieces piles pilots pins
        pipes pitches pizzas plants plates platforms players pockets poems
        poets points poles policies pools populations positions pots
        potatoes pounds powers practices prayers pressures prices prints
        priorities prizes procedures processes products professors profits
        programs projects promises proofs properties proposals protests
        purposes qualities quarters queens queues quotes races radios
        rains ranges rates reactions readings reasons receipts recipes
        records regions relations remarks remedies rents repairs repeats
        replies reports requests requirements reserves resources respects
        responses results returns revenues reviews rewards rhythms rings
        rises risks rivers rocks roles roofs roots ropes rows rules rushes
        salads salaries sales samples sands sauces scales scenes schedules
        schemes scores seas seasons seats sections sectors seeds selections
        senses sentences series services sessions settings shades shadows
        shapes shares sheets shelves shells shifts ships shirts shoes shops
        shores shoulders shouts shows showers sights signs silvers singers
        sinks sites situations sizes skills skins skirts skies sleeps
        slices slides slips smells smiles snows socks sofas soils soldiers
        solutions souls sounds sources spaces speakers speeds spells
        spirits splits spoons sports spots squares stages stairs stamps
        stars statements stations steaks steps sticks stones stomachs
        stores storms stories strains strangers strategies strengths
        stresses stretches strings strokes structures struggles studios
        studies subjects substances successes suggestions suits summers
        supplies surfaces surgeons surprises suspects swans switches
        symbols systems tables tails tales talks tanks taps targets tasks
        taxes teams tears techniques temperatures temples tensions terms
        tests texts themes theories tickets ties tigers tiles tips tires
        titles toilets tones tongues tools teeth tops topics tours tourists
        towels towers towns toys tracks trades trails transfers travels
        treats treatments trees trends trials triangles tricks trips
        troubles trucks trusts truths tubes tunes tunnels turns twins types
        uncles unions units values vegetables vehicles versions victims
        victories views villages visits voices volumes votes wages waits
        walks walls wallets warnings wastes waves weapons weddings weekends
        weights wheels whispers winds windows wings winners winters wires
        wishes witnesses wonders woods workers worms wounds writers yards
        zones blouses skirts shirts pants coats jackets sweaters scarves
        socks boots sandals sneakers uniforms suits belts helmets glasses
        necklaces rings backpacks umbrellas pockets sleeves buttons
        fabrics closets drawers shelves
        plural forms batch two
        academies architects astronauts attorneys auditors bakers bankers
        barbers biologists butchers carpenters cashiers chefs chemists
        clerks coaches consultants contractors counselors dentists
        designers detectives diplomats directors editors educators
        electricians engineers entrepreneurs executives farmers florists
        gardeners geologists governors guards historians inspectors
        instructors interpreters janitors journalists librarians linguists
        maids mayors merchants miners musicians novelists painters
        pharmacists philosophers photographers physicians physicists
        plumbers politicians principals producers programmers prosecutors
        psychiatrists psychologists publishers reporters researchers
        sailors scientists sculptors secretaries senators sergeants
        shepherds surgeons surveyors tailors technicians translators
        tutors veterinarians waiters welders zoologists accountants
        atoms molecules electrons protons neutrons nuclei cells chromosomes
        genes proteins enzymes bacteria viruses vaccines antibodies
        hormones neurons lungs livers kidneys stomachs intestines skeletons
        arteries veins tissues organs species mutations ecosystems habitats
        galaxies planets asteroids comets meteors orbits volcanoes
        earthquakes tsunamis hurricanes tornadoes typhoons floods minerals
        crystals diamonds fossils dinosaurs mammals reptiles amphibians
        insects spiders butterflies bees ants mosquitoes beetles worms
        eagles hawks owls sparrows pigeons crows doves swans ducks geese
        penguins dolphins whales sharks crabs lobsters shrimps frogs toads
        lizards snakes turtles crocodiles elephants giraffes lions tigers
        leopards zebras monkeys gorillas pandas koalas kangaroos camels
        horses sheep goats pigs rabbits squirrels hamsters foxes wolves
        equations formulas theorems proofs hypotheses variables constants
        percentages ratios decimals fractions integers primes graphs charts
        axes diameters radii cylinders cones pyramids angles degrees
        kilograms grams liters meters kilometers centimeters miles inches
        feet pounds ounces almonds appetizers apricots asparagus avocados
        bagels bananas barbecues beans beets berries biscuits blackberries
        blueberries carrots cakes calories candies caramels cauliflowers
        cereals cheesecakes cherries chickens chips chocolates ciders
        cookies crabs crackers cranberries crepes croissants cucumbers
        cupcakes curries custards desserts donuts dumplings eggplants figs
        frostings grapes guavas hams hamburgers icings jams jellies kales
        ketchups kiwis lasagnas leeks lemons lentils lettuces limes
        lobsters lollipops lychees mangos marshmallows meatballs melons
        milkshakes mints muffins mushrooms mustards noodles nutmegs
        oatmeals omelets onions oysters pancakes papayas parsnips pastas
        pastries peaches peanuts pears pecans peppers pepperonis persimmons
        pickles pies pineapples pistachios pizzas plums pomegranates
        popsicles pretzels puddings pumpkins quiches radishes raisins
        raspberries raviolis roasts salads salamis salmons salsas
        sandwiches sausages shrimps sorbets soybeans spinaches squashes
        steaks stews strawberries syrups tangerines tarts toasts tofus
        tomatoes tortillas truffles turkeys turnips waffles walnuts
        watermelons zucchinis appliances armchairs attics awnings balconies
        bathtubs blankets blenders bookcases bookshelves brooms buckets
        cabinets candles carpets cellars chandeliers closets coasters
        couches countertops cupboards curtains cushions dishwashers
        doormats dressers dryers dustpans fireplaces freezers grills
        hampers heaters humidifiers kettles lamps lanterns lightbulbs
        mattresses microwaves mirrors mops napkins ornaments ovens pans
        pillows plugs plungers racks radiators razors remotes rugs shelves
        showers sinks soaps sofas sponges stools stoves suitcases tabletops
        teapots thermometers thermostats tissues toasters towels vacuums
        vases wardrobes archipelagos bays beaches bluffs bogs canyons
        capes cliffs coasts creeks deltas deserts dunes estuaries fjords
        forests geysers gorges groves highlands hollows islands isthmuses
        jungles lagoons lakes marshes meadows mesas mountains oases oceans
        peaks peninsulas plains plateaus ponds prairies reefs ridges
        savannas shores straits summits swamps terraces tundras valleys
        waterfalls wetlands woods blizzards breezes clouds cyclones
        drizzles droughts fogs frosts gales hails hazes hurricanes
        lightnings mists monsoons rainbows storms thunders tornadoes
        typhoons whirlwinds winds
        verb inflections batch two
        calculated calculating canceled canceling captured capturing cared
        caring carried carrying caught catching caused causing challenged
        challenging changed changing charged charging chased chasing
        cheated cheating checked checking cheered cheering chewed chewing
        chose choosing chopped chopping cleaned cleaning cleared clearing
        climbed climbing closed closing collected collecting combined
        combining comforted comforting compared comparing complained
        complaining completed completing concentrated concentrating
        confirmed confirming connected connecting convinced convincing
        cooked cooking copied copying corrected correcting counted counting
        covered covering crashed crashing crossed crossing cried crying
        cured curing curved curving dared daring decorated decorating
        delivered delivering demonstrated demonstrating denied denying
        departed departing described describing deserved deserving
        destroyed destroying detected detecting developed developing
        disagreed disagreeing disappeared disappearing discovered
        discovering discussed discussing displayed displaying distributed
        distributing disturbed disturbing dived diving divided dividing
        dragged dragging drained draining drawn drawing drifted drifting
        drilled drilling driven driving drowned drowning dried drying dumped
        dumping earned earning echoed echoing edited editing educated
        educating eliminated eliminating embraced embracing encouraged
        encouraging entertained entertaining escaped escaping established
        establishing evaluated evaluating examined examining exchanged
        exchanging excused excusing exercised exercising expanded expanding
        expected expecting explained explaining explored exploring exposed
        exposing extended extending failed failing faded fading fastened
        fastening fetched fetching filled filling filmed filming financed
        financing fixed fixing flashed flashing floated floating flooded
        flooding folded folding followed following forced forcing forecasted
        forecasting forgiven forgiving formed forming founded founding
        froze freezing frightened frightening fried frying gathered
        gathering generated generating grabbed grabbing graduated
        graduating greeted greeting grinned grinning ground grinding gripped
        gripping guessed guessing handed handing handled handling happened
        happening harmed harming healed healing heated heating hesitated
        hesitating hid hiding highlighted highlighting hired hiring hit
        hitting hooked hooking hugged hugging hunted hunting hurried
        hurrying identified identifying ignored ignoring imagined imagining
        impressed impressing improved improving included including
        indicated indicating informed informing insisted insisting inspired
        inspiring instructed instructing intended intending interrupted
        interrupting introduced introducing invented inventing invested
        investing invited inviting involved involving joined joining judged
        judging jumped jumping kicked kicking kissed kissing knelt kneeling
        knocked knocking labeled labeling landed landing laughed laughing
        launched launching leaked leaking leaned leaning leapt leaping lent
        lending lifted lifting linked linking loaded loading located
        locating locked locking looked looking lowered lowering mailed
        mailing maintained maintaining managed managing marched marching
        marked marking married marrying matched matching measured measuring
        melted melting mended mending mentioned mentioning merged merging
        minded minding missed missing monitored monitoring motivated
        motivating mounted mounting nodded nodding observed observing
        obtained obtaining occupied occupying occurred occurring offended
        offending operated operating opposed opposing organized organizing
        overcome overcoming painted painting parked parking passed passing
        pasted pasting patted patting paid paying performed performing
        permitted permitting persuaded persuading picked picking piled
        piling pitched pitching played playing pointed pointing polished
        polishing popped popping poured pouring praised praising prayed
        praying predicted predicting preferred preferring prepared
        preparing presented presenting preserved preserving pressed
        pressing pretended pretending prevented preventing printed printing
        produced producing promised promising promoted promoting pronounced
        pronouncing proposed proposing protected protecting proved proving
        published publishing pulled pulling pumped pumping punched punching
        punished punishing purchased purchasing pushed pushing quit
        quitting raised raising reached reaching reacted reacting realized
        realizing received receiving recognized recognizing recommended
        recommending recovered recovering recruited recruiting recycled
        recycling reduced reducing reflected reflecting refused refusing
        registered registering regretted regretting rejected rejecting
        related relating relaxed relaxing released releasing relied relying
        remained remaining reminded reminding removed removing renamed
        renaming repaired repairing repeated repeating replaced replacing
        replied replying reported reporting represented representing
        requested requesting required requiring rescued rescuing researched
        researching resigned resigning resisted resisting resolved
        resolving respected respecting responded responding rested resting
        restored restoring restricted restricting retired retiring returned
        returning revealed revealing reviewed reviewing revised revising
        rewarded rewarding ridden riding rinsed rinsing risen rising risked
        risking roared roaring roasted roasting rolled rolling rotated
        rotating rubbed rubbing ruined ruining rushed rushing saved saving
        scanned scanning scared scaring scattered scattering screamed
        screaming sealed sealing searched searching seized seizing selected
        selecting sold selling separated separating served serving settled
        settling sewed sewing shaken shaking shaped shaping shared sharing
        sharpened sharpening shaved shaving sheltered sheltering shifted
        shifting shined shining shocked shocking shopped shopping shouted
        shouting shoved shoving showed showing shrank shrinking signed
        signing simplified simplifying sang singing sketched sketching
        slammed slamming slapped slapping slid sliding slipped slipping
        smelled smelling smiled smiling smoked smoking snapped snapping
        sneezed sneezing sniffed sniffing soaked soaking soared soaring
        spared sparing spelled spelling spent spending spilled spilling
        spun spinning spoiled spoiling sponsored sponsoring sprayed
        spraying spread spreading squeezed squeezing stabbed stabbing
        stamped stamping stared staring steered steering stepped stepping
        stuck sticking stirred stirring stored storing stretched stretching
        struck striking stripped stripping studied studying stuffed
        stuffing submitted submitting succeeded succeeding sucked sucking
        suffered suffering suggested suggesting suited suiting supported
        supporting supposed supposing surrounded surrounding survived
        surviving swallowed swallowing swore swearing swept sweeping swam
        swimming swung swinging tackled tackling tapped tapping tasted
        tasting teased teasing tightened tightening touched touching toured
        touring traced tracing traded trading trained training translated
        translating treated treating trembled trembling trusted trusting
        tried trying tuned tuning twisted twisting unloaded unloading
        unlocked unlocking unpacked unpacking updated updating upgraded
        upgrading urged urging vanished vanishing varied varying visited
        visiting volunteered volunteering voted voting waited waiting waked
        waking wandered wandering warned warning washed washing wasted
        wasting watched watching waved waving weighed weighing whistled
        whistling widened widening wiped wiping wondered wondering worried
        worrying wrapped wrapping yelled yelling zipped zipping zoomed
        zooming
        plural forms batch three
        airlines airstrips backpacks bicycles boats boulevards bridges
        brooks canals carriages carts cathedrals causeways checkpoints
        coaches cockpits compartments corridors cruises decks departures
        destinations detours driveways embassies expeditions expressways
        fares ferries freeways gateways hostels itineraries jets journeys
        junctions landmarks lanes layovers locomotives metros milestones
        motels overpasses passports pathways pavements pedestrians piers
        platforms railways resorts runways scooters sidewalks skylines
        subways suites terminals timetables tolls trams trolleys underpasses
        vessels visas voyages yachts abdomens allergies ambulances arteries
        bandages biopsies bladders bones brains breasts breaths capsules
        clinics colons diagnoses doses epidemics fevers fractures germs
        glands hearts hospitals immunities infections inflammations
        injections injuries insulines intestines joints kidneys ligaments
        lymphs medicines metabolisms muscles nerves nutritions ointments
        organs pathologies pharmacies physicians pills placebos plasmas
        prescriptions prostates pulses quarantines remedies retinas
        surgeries syndromes therapies tissues tonsils transplants traumas
        treatments tumors ultrasounds vaccinations vaccines veins vertebrae
        vitamins wounds xrays acquisitions affiliates appraisals assets
        auctions benchmarks bids bonds brokerages budgets charters
        commissions commodities concessions consortiums contracts coupons
        creditors debts deeds deposits discounts earnings embargoes
        endorsements enterprises estimates franchises funds guarantees
        incomes indemnities indentures insurers kickbacks ledgers leases
        liens markups monopolies payrolls pensions premiums principals
        procurements profits prospectuses quotations receipts recessions
        refunds remittances rents royalties stakes subsidies syndicates
        tariffs tenders transactions treasuries trusts vouchers warrants
        accommodations admissions agendas allergies allowances amenities
        elements calendar numbers adverbs final batch
        lithium beryllium boron fluorine neon sodium magnesium aluminum
        silicon phosphorus sulfur chlorine potassium calcium scandium
        titanium vanadium chromium manganese iron cobalt nickel zinc
        gallium germanium arsenic selenium bromine krypton rubidium
        strontium yttrium zirconium niobium molybdenum silver cadmium
        indium tin antimony tellurium iodine xenon cesium barium lanthanum
        cerium tungsten platinum mercury lead bismuth uranium radium
        thorium plutonium
        tuesday wednesday thursday friday january february march april may
        june july august september october november december weekdays
        weekends springtime wintertime summertime fall
        eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen
        nineteen twenty thirty forty fifty sixty seventy eighty ninety
        hundred thousand million billion trillion dozen half quarter
        double triple single zero first second third fourth fifth sixth
        seventh eighth ninth tenth once twice thrice
        rarely sometimes always never often usually quickly slowly suddenly
        finally recently immediately eventually gradually frequently
        occasionally constantly repeatedly deliberately accidentally
        carefully quietly loudly gently firmly deeply widely greatly highly
        strongly barely hardly merely purely simply truly utterly wholly
        partly fully almost nearly quite rather pretty fairly somewhat
        perhaps maybe certainly definitely absolutely exactly precisely
        approximately especially particularly mainly mostly largely
        generally normally typically commonly traditionally officially
        formally informally publicly privately personally individually
        collectively jointly separately independently mutually equally
        unequally fairly unfairly legally illegally morally ethically
        physically mentally emotionally psychologically spiritually
        financially economically politically socially culturally
        historically geographically scientifically technically
        commercially industrially agriculturally environmentally globally
        locally nationally regionally internationally universally
        final supplement
        abroad anyhow anyone anything anyway anywhere everyone everything
        everywhere someone something somewhere nobody nothing nowhere
        somebody everybody anybody
        arise arose arisen awake awoke awoken bear bore borne befall
        befallen behold beheld beset betide bide bid bidden bind bound
        bleed bled breed bred
        airplane automobile avenue backyard banknote barbershop bookstore
        brainstorm brotherhood businessperson byproduct carpool classmate
        courthouse daybreak doorstep fingertips footsteps halfway headache
        heartbreak hometown lifeline lifelong mailbox newsstand nighttime
        overcoat passbook payday photocopy postcard raincoat schoolbook
        seashore shoelace showtime snowball someday sunflower toothbrush
        townhouse watchdog weatherman woodwork workbook
        baffle babble bicker cackle cajole chortle chuckle chide clamber
        clamor clobber dangle dawdle dither dribble fiddle fumble gargle
        gawk giggle glisten grovel grumble gush heckle haggle hassle
        jostle juggle kindle
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
}
