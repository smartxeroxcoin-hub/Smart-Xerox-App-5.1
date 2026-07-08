// Smart X Point Auth Guard - Firebase Auth State Protection
(function() {
    if (window.location.pathname.endsWith('login.html')) {
        return;
    }

    // Allow customer QR ordering portal without login requirement
    if (window.location.search.includes('shop=')) {
        return;
    }

    if (typeof firebase !== 'undefined') {
        try {
            if (!firebase.apps.length) {
                firebase.initializeApp({
                    apiKey: "AIzaSyDummyKeyForSmartXPointPortal",
                    authDomain: "smart-x-point.firebaseapp.com",
                    projectId: "smart-x-point"
                });
            }
            
            firebase.auth().onAuthStateChanged(user => {
                if (!user) {
                    const sessionActive = sessionStorage.getItem('sxp_auth_active');
                    if (!sessionActive && !window.location.search.includes('shop=')) {
                        window.location.replace('login.html');
                    }
                }
            });
        } catch (e) {
            console.error("Auth Guard Error:", e);
        }
    } else {
        const sessionActive = sessionStorage.getItem('sxp_auth_active');
        if (!sessionActive && !window.location.pathname.endsWith('login.html') && !window.location.search.includes('shop=')) {
            window.location.replace('login.html');
        }
    }
})();
