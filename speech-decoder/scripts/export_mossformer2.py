#!/usr/bin/env python3
"""
Exports ClearerVoice MossFormer2_SS_16K (Apache-2.0 code and weights) to a fixed 2 s ONNX window.
The printed parity test compares torch and ONNX outputs; check it before shipping.

  git clone --depth 1 https://github.com/modelscope/ClearerVoice-Studio.git
  curl -L -o mf2ss.pt https://huggingface.co/alibabasglab/MossFormer2_SS_16K/resolve/main/last_best_checkpoint.pt
  pip install torch einops rotary-embedding-torch torchinfo onnx onnxruntime
  python export_mossformer2.py ClearerVoice-Studio/clearvoice mf2ss.pt mossformer2_ss_16k.onnx
  python -c "from onnxruntime.quantization import quantize_dynamic,QuantType as Q; quantize_dynamic('mossformer2_ss_16k.onnx','mossformer2_ss_16k_int8.onnx',weight_type=Q.QInt8,op_types_to_quantize=['MatMul','Gemm'])"
"""
import sys, types
import numpy as np
import torch

repo, ckpt, out = sys.argv[1:4]
import os
# Import the model package without running clearvoice/__init__.py (it pulls in unrelated training dependencies).
for name, sub in (("clearvoice", "clearvoice"), ("clearvoice.models", "clearvoice/models")):
    mod = types.ModuleType(name); mod.__path__ = [os.path.join(repo, sub)]; sys.modules[name] = mod
from clearvoice.models.mossformer2_ss.mossformer2 import MossFormer2_SS_16K

args = types.SimpleNamespace(num_spks=2, encoder_kernel_size=16, encoder_embedding_dim=512,
                             mossformer_sequence_dim=512, num_mossformer_layer=24)
net = MossFormer2_SS_16K(args).model
state = torch.load(ckpt, map_location="cpu", weights_only=True)   # tensor-only unpickler
state = state.get("model", state)
net.load_state_dict({k.replace("module.", ""): v for k, v in state.items()}, strict=True)
net.eval()


class Wrap(torch.nn.Module):
    def __init__(self, m):
        super().__init__(); self.m = m
    def forward(self, x):                      # x: [1, 32000]
        a, b = self.m(x)
        return torch.stack([a, b], dim=1)      # [1, 2, 32000]


wrap = Wrap(net).eval()
x = torch.randn(1, 32000) * 0.1
with torch.no_grad():
    ref = wrap(x).numpy()
    torch.onnx.export(wrap, x, out, input_names=["mix"], output_names=["sources"], opset_version=17,
                      do_constant_folding=True)
import onnxruntime as ort
got = ort.InferenceSession(out).run(None, {"mix": x.numpy()})[0]
print("max abs diff torch vs onnx:", float(np.abs(ref - got).max()), "shape", got.shape)
