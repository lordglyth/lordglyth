"""Fast self-tests for the independent SnoPlay film assembly pipeline."""
import shutil
import tempfile
import unittest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import server


class StudioSmokeTests(unittest.TestCase):
    def test_storyboard_covers_exact_requested_duration(self):
        plan=server.distribute_duration(server.fallback_plan("Foxes at an academy", 61, "anime"),61)
        self.assertEqual(round(sum(x["duration"] for x in plan),1),61.0)
        self.assertTrue(all(x["visual"] for x in plan))
        self.assertTrue(all(x["duration"]>=2 for x in plan))

    def test_title_card_image(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/"card.png"
            server.title_card(p,"Magical opening","Blue fireflies gather in a garden","9:16",0)
            self.assertTrue(p.is_file())
            from PIL import Image
            with Image.open(p) as im:
                self.assertEqual(im.size,(720,1280))

    @unittest.skipUnless(shutil.which("ffmpeg"),"FFmpeg not found on PATH")
    def test_five_second_mp4_from_storyboard(self):
        with tempfile.TemporaryDirectory() as folder:
            old=server.DATA
            try:
                server.DATA=Path(folder)
                pid="a"*32
                (server.DATA/pid).mkdir()
                scenes=server.distribute_duration(server.fallback_plan("Small luminescent fox",5,"anime"),5)
                project={"id":pid,"title":"Smoke test","prompt":"Fox","style":"anime",
                         "seconds":5,"ratio":"16:9","planner":"fallback","scenes":scenes,
                         "references":[],"soundtrack":None,"voiceover":None}
                server.save_project(project)
                server.TASKS[pid]={"state":"running","phase":"test","progress":0,"error":""}
                server.render(pid)
                result=(server.DATA/pid/"movie.mp4")
                self.assertTrue(result.exists())
                self.assertGreater(result.stat().st_size,5000)
            finally:
                server.DATA=old
                server.TASKS.pop("a"*32,None)

if __name__=="__main__":
    unittest.main()
