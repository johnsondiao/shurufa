package com.personal.ime.util

/**
 * 内置英文词表，按常用度大致排序（越靠前越常用）：
 * 高频功能词 → 日常用语 → 计算机专业词汇。
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
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
}
