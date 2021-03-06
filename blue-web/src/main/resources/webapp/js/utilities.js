Date.prototype.getWeek = function() {
    var onejan = new Date(this.getFullYear(),0,1);
    return Math.ceil((((this - onejan) / 86400000) + onejan.getDay()+1)/7);
};
var jsonToPostParameters = function (json) {
    return Object.keys(json).map(function(k) {
        return encodeURIComponent(k) + '=' + encodeURIComponent(json[k]);
    }).join('&');
};
function getFileNameExtension(filename) {
    var parts = filename.split('.');
    return parts[parts.length - 1];
}
function getFileType(filename) {
    var ext = getFileNameExtension(filename);
    switch (ext.toLowerCase()) {
      case 'jpg':
      case 'jpeg':
      case 'gif':
      case 'bmp':
      case 'png':
        return 'image';
      case 'm4v':
      case 'avi':
      case 'mpg':
      case 'mp4':
        return 'video';
      case 'pdf':
        return 'pdf';
      case 'txt':
        return 'text';
      case 'tex':
        return 'latex';
    }
    return 'other';
}
function clone(obj) {
    // Handle the 3 simple types, and null or undefined
    if (null == obj || "object" != typeof obj) return obj;

    // Handle Date
    if (obj instanceof Date) {
        var copy = new Date();
        copy.setTime(obj.getTime());
        return copy;
    }

    // Handle Array
    if (obj instanceof Array) {
        var copy = [];
        for (var i = 0, len = obj.length; i < len; i++) {
            copy[i] = clone(obj[i]);
        }
        return copy;
    }

    // Handle Object
    if (obj instanceof Object) {
        var copy = {};
        for (var attr in obj) {
            if (obj.hasOwnProperty(attr)) copy[attr] = clone(obj[attr]);
        }
        return copy;
    }

    throw new Error("Unable to copy obj! Its type isn't supported.");
}
function get_radom_color () {
  return (function(m,s,c){return (c ? arguments.callee(m,s,c-1) : '#') +
  s[m.floor(m.random() * s.length)];})(Math,'0123456789ABCDEF',5);
}
function findPosX(obj) {
    var curleft = 0;
    if (obj.offsetParent) {
        while (1) {
            curleft+=obj.offsetLeft;
            if (!obj.offsetParent) {
                break;
            }
            obj=obj.offsetParent;
        }
    } else if (obj.x) {
        curleft+=obj.x;
    }
    return curleft;
};

function findPosY(obj) {
    var curtop = 0;
    if (obj.offsetParent) {
        while (1) {
            curtop+=obj.offsetTop;
            if (!obj.offsetParent) {
                break;
            }
            obj=obj.offsetParent;
        }
    } else if (obj.y) {
        curtop+=obj.y;
    }
    return curtop;
};

var convertToViewportPoint  = function (x, y, dimension) {
    var transform = [
      dimension.scale,
      0,
      0,
      -dimension.scale,
      0,
      dimension.height
    ];

    var xt = x * transform[0] + y * transform[2] + transform[4];
    var yt = x * transform[1] + y * transform[3] + transform[5];
    return [xt, yt];
};